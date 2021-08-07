package Sodor
{

import chisel3._
import chisel3.util._
import Common._
import Common.Instructions._
import Constants._

//ctl begin
class MTodpathio(implicit val conf: SodorCoreParams) extends Bundle()
{
   val csignal = Input(UInt(2.W)) //给TPU的指令控制 
   val addr1 = Input(UInt(32.W)) //A或B矩阵读取地址
   val addr2 = Input(UInt(32.W)) //结果C矩阵写回地址
   val stall  = Output(Bool())//stall控制，在cpath中与其他逻辑共同构成流水线启停的控制
}

class MpathIo(implicit val conf: SodorCoreParams) extends Bundle() 
{
   val dpathio = new MTodpathio() //与datapath相连
   val tpathio= Flipped(new Tiletoctl()) //与tile相连
   val dmem = new MemPortIo(conf.xprlen) //与dmem相连
}

class TPUcontroler(implicit val conf: SodorCoreParams) extends Module
{
   val io= IO(new MpathIo())  //实例化接口对象
   io:=DontCare
   val tempBuffer = Reg(UInt(32.W)) //暂存32位内存读取结果
   val Bbuffer = Reg(Vec(16,UInt(8.W))) //暂存B矩阵从内存中取回的结果并直接与每个PE的b寄存器相连
   val Abuffer = Reg(Vec(4,UInt(8.W))) //暂存A矩阵从内存中取回的结果，按照顺序向Tile中逐步输入
   val Cbuffer = Reg(Vec(16,UInt(16.W))) //暂存从Tile中取回的计算结果，并将写回dmem
   io.tpathio.weight_in := Bbuffer //直接与Tile中的输入接口相连
   io.tpathio.din := Abuffer //直接与Tile中的输入接口相连，按照时序输入
   val clken1 = Wire(Bool()) //控制指令1的计时器启停
   val clken2 = Wire(Bool()) //控制指令2的计时器启停
   val(a1,b1)=Counter(clken1,3)
   val(a2,b2)=Counter(clken2,16)
   clken1:=io.dmem.resp.valid&&io.dpathio.csignal===1.U
   clken2:=io.dmem.resp.valid&&io.dpathio.csignal===2.U
   io.dpathio.stall := (io.dpathio.csignal=/=0.U)&&((clken1 && a1<=3.U)||(clken2 && a2<=6.U))
   when (a1===3.U)
   {
      clken1:=false.B
   }
   when (a2===16.U)
   {
      clken2:=false.B
   }
   when (io.dpathio.csignal === 0.U){
      
   }
   .elsewhen (io.dpathio.csignal === 1.U){
      //io.dmem.req.fcn:=M_XRD
      //io.dmem.req.bits.typ := MT_WU
      io.dmem.req.bits.addr := io.dpathio.addr1+a1*4.U
      tempBuffer := io.dmem.resp.bits.data
      Bbuffer(a1*4.U+0.U) := tempBuffer(7,0) //? 7-0
      Bbuffer(a1*4.U+1.U) := tempBuffer(15,8)
      Bbuffer(a1*4.U+2.U) := tempBuffer(23,15)
      Bbuffer(a1*4.U+3.U) := tempBuffer(31,24)
      
   }
   .otherwise{ 
      when(a2<=3.U){
        // io.dmem.req.bits.fcn:=M_XRD
        // io.dmem.req.bits.typ := MT_WU 写到指令的MEM类型里，在cpath里实现内存模式的控制
         io.dmem.req.bits.addr := io.dpathio.addr1+a2*4.U
         tempBuffer := io.dmem.resp.bits.data
         Abuffer(0) := tempBuffer(7,0) //? 7-0
         Abuffer(1) := tempBuffer(15,8)
         Abuffer(2) := tempBuffer(23,15)
         Abuffer(3) := tempBuffer(31,24)
      }
      when(a2===2.U) //clk 3
      {
         Cbuffer(0) := io.tpathio.mac_out(0);
      }
      when(a2 === 3.U) //clk 4
      {
         Cbuffer(0+4*1) := io.tpathio.mac_out(0);
         Cbuffer(1+4*0) := io.tpathio.mac_out(1);
      }
      when(a2 === 4.U) //clk 5
      {
         Cbuffer(0+4*2) := io.tpathio.mac_out(0);
         Cbuffer(1+4*1) := io.tpathio.mac_out(1);
         Cbuffer(2+4*0) := io.tpathio.mac_out(2);
      }
      when(a2 === 5.U) //clk 6
      {
         Cbuffer(0+4*3) := io.tpathio.mac_out(0);
         Cbuffer(1+4*2) := io.tpathio.mac_out(1);
         Cbuffer(2+4*1) := io.tpathio.mac_out(2);
         Cbuffer(3+4*0) := io.tpathio.mac_out(3);
      }
      when(a2 === 6.U) //clk 7
      {
         Cbuffer(1+4*3) := io.tpathio.mac_out(1);
         Cbuffer(2+4*2) := io.tpathio.mac_out(2);
         Cbuffer(3+4*1) := io.tpathio.mac_out(3);
      }
      when(a2 === 7.U) //clk 8
      {
         Cbuffer(2+4*3) := io.tpathio.mac_out(2);
         Cbuffer(3+4*2) := io.tpathio.mac_out(3);
      }
      when(a2 === 8.U) //clk 9
      {
         Cbuffer(3+4*3) := io.tpathio.mac_out(3);
      }
      when(a2 > 8.U)
      {
         io.dmem.req.bits.fcn := M_XWR
         io.dmem.req.bits.typ := MT_WU
         io.dmem.req.bits.addr := io.dpathio.addr2+(a2-9.U)*4.U
         io.dmem.req.bits.data := Cat(Cbuffer((a2-9.U)*2.U),Cbuffer((a2-9.U)*2.U+1.U))
      }
   }
}
//ctl end

}
