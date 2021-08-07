package Sodor
{
import chisel3._
import chisel3.util._
import Common._
import Common.Instructions._
import Constants._

//tile begin
class Tiletoctl extends Bundle{
    val din        = Input(Vec(4, UInt(8.W)))
    val weight_in  = Input(Vec(16, UInt(8.W))) 
    val in_control  = Input(Vec(4, new PEControl))
    val mac_out       = Output(Vec(4, UInt(16.W)))
}

class Tileio extends Bundle{
    val ctlio = new Tiletoctl
    val dout       = Output(Vec(4, UInt(16.W)))
    val out_control=Output(Vec(4, new PEControl))
}

class Tile extends Module {
  val io = IO(new Tileio)    
  io:=DontCare

  val psum=RegInit(VecInit(0.U(8.W), 0.U(8.W),0.U(8.W),0.U(8.W)))

  val din_a=Reg(Vec(4,UInt(8.W)))
  din_a(0):=io.ctlio.din(0)
  din_a(1):=ShiftRegister(io.ctlio.din(1),1)
  din_a(2):=ShiftRegister(io.ctlio.din(2),2)
  din_a(3):=ShiftRegister(io.ctlio.din(3),3)
  val tile = Seq.fill(4,4)(Module(new PE))
  val tileT = tile.transpose

  for(ii <- 0 until 4)
    for(jj <- 0 until 4)
    {
        tile(ii)(jj).io.weight_in:= io.ctlio.weight_in(ii*4+jj)
    }
  

  

  // TODO: abstract hori/vert broadcast, all these connections look the same
  // Broadcast 'a' horizontally across the Tile
  for (r <- 0 until 4) {
    tile(r).foldLeft(din_a(0)) {
      case (din_a, pe) =>
        pe.io.din :=din_a
        pe.io.dout
    }
  }

  // Broadcast 'psum' vertically across the Tile
  for (c <- 0 until 4) {
    tileT(c).foldLeft(psum(0)) {
      case (psum, pe) =>
        pe.io.psum_in := psum
        pe.io.mac_out 
    }
  }

  // Broadcast 'control' vertically across the Tile
  for (c <- 0 until 4) {
    tileT(c).foldLeft(io.ctlio.in_control(c)) {
      case (in_ctrl, pe) =>
        pe.io.in_control := in_ctrl
        pe.io.out_control
    }
  }

  // Drive the Tile's bottom IO
  for (c <- 0 until 4) {
    io.ctlio.mac_out(c) := tile(3)(c).io.mac_out
    io.out_control(c) := tile(3)(c).io.out_control
  }

}
//tile end

}
