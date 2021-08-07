// See LICENSE for license details.
// NOTE: This is mostly a copy from the Berkeley Rocket-chip csr file. It is
//       overkill for a small, embedded processor. 

package Common


import chisel3._
import chisel3.util.{PopCount, Mux1H, Cat}
import Util._
import collection.mutable.LinkedHashMap
import Common.Constants.MTVEC

class MStatus extends Bundle {
    // not truly part of mstatus, but convenient
  val prv = UInt(PRV.SZ.W) // not truly part of mstatus, but convenient
  val unimp5 = UInt(14.W)
  val mprv = Bool()
  val unimp4 = UInt(2.W)
  val mpp = UInt(2.W)
  val unimp3 = UInt(3.W)
  val mpie = Bool()
  val unimp2 = UInt(3.W)
  val mie = Bool()
  val unimp1 = UInt(3.W)
}

object PRV
{
  val SZ = 2
  val M = 3
}

class MIP extends Bundle {
  val unimp4 = UInt(4.W)
  val meip = Bool()
  val unimp3 = UInt(3.W)
  val mtip = Bool()
  val unimp2 = UInt(3.W)
  val msip = Bool()
  val unimp1 = UInt(3.W)
}

class PerfCounterIO(implicit val conf: SodorConfiguration) extends Bundle{
  val inc = Input(UInt(conf.xprlen.W))
}

object CSR
{
  // commands
  val SZ = 3.W
  val X = 0.asUInt(SZ)
  val N = 0.asUInt(SZ)
  val W = 1.asUInt(SZ)
  val S = 2.asUInt(SZ)
  val C = 3.asUInt(SZ)
  val I = 4.asUInt(SZ)
  val R = 5.asUInt(SZ)

  val ADDRSZ = 12
  val firstCtr = CSRs.cycle
  val firstCtrH = CSRs.cycleh
  val firstHPC = CSRs.hpmcounter3
  val firstHPCH = CSRs.hpmcounter3h
  //val firstHPE = CSRs.mhpmevent3
  val firstMHPC = CSRs.mhpmcounter3
  val firstMHPCH = CSRs.mhpmcounter3h
  val firstHPM = 3
  val nCtr = 32
  val nHPM = nCtr - firstHPM
  val hpmWidth = 40
}

class CSRFileIO(implicit val conf: SodorConfiguration) extends Bundle {
  val hartid = Input(UInt(conf.xprlen.W))
  val rw = new Bundle {
    val cmd = Input(UInt(CSR.SZ))
    val addr = Input(UInt(CSR.ADDRSZ.W))
    val rdata = Output(UInt(conf.xprlen.W))
    val wdata = Input(UInt(conf.xprlen.W))
  }

  val csr_stall = Output(Bool())
  val eret = Output(Bool())
  val singleStep = Output(Bool())

  val decode = new Bundle {
    val read_illegal = Output(Bool())
    val write_illegal = Output(Bool())
    val system_illegal = Output(Bool())
  }

  val status = Output(new MStatus())
  val xcpt = Input(Bool())
  val cause = Input(UInt(conf.xprlen.W))
  val tval = Input(UInt(conf.xprlen.W))
  val evec = Output(UInt(conf.xprlen.W))
  val illegal = Input(Bool())
  val retire = Input(Bool())
  val pc = Input(UInt(conf.xprlen.W))
  val time = Output(UInt(conf.xprlen.W))
  val counters = Vec(60, new PerfCounterIO)

}

class CSRFile(implicit conf: SodorConfiguration) extends Module
{
  val io = IO(new CSRFileIO)
  io := DontCare

  val reset_mstatus = WireInit(0.U.asTypeOf(new MStatus))
  reset_mstatus.mpp := PRV.M
  reset_mstatus.prv := PRV.M
  val reg_mstatus = RegInit(reset_mstatus)
  val reg_mepc = Reg(UInt(conf.xprlen.W))
  val reg_mcause = Reg(UInt(conf.xprlen.W))
  val reg_mtval = Reg(UInt(conf.xprlen.W))
  val reg_mscratch = Reg(UInt(conf.xprlen.W))
  val reg_mtimecmp = Reg(UInt(conf.xprlen.W))
  val reg_medeleg = Reg(UInt(conf.xprlen.W))

  val reg_mip = RegInit(0.U.asTypeOf(new MIP))
  val reg_mie = RegInit(0.U.asTypeOf(new MIP))
  val reg_wfi = RegInit(false.B)
  val reg_mtvec = Reg(UInt(conf.xprlen.W))

  val reg_time = WideCounter(64)
  val reg_instret = WideCounter(64, io.retire)

  val reg_mcounteren = Reg(UInt(32.W))
  val reg_hpmcounter = io.counters.map(c => WideCounter(CSR.hpmWidth, c.inc, reset = false))

  val new_prv = WireInit(reg_mstatus.prv)
  reg_mstatus.prv := new_prv

  val reg_dscratch = Reg(UInt(conf.xprlen.W))
  val reg_singleStepped = Reg(Bool())

  val system_insn = io.rw.cmd === CSR.I
  val cpu_ren = io.rw.cmd =/= CSR.N && !system_insn

  val read_mstatus = io.status.asUInt
  val isa_string = "I"
  val misa = BigInt(0x40000000) | isa_string.map(x => 1 << (x - 'A')).reduce(_|_)
  val impid = 0x8000 // indicates an anonymous source, which can be used
                     // during development before a Source ID is allocated.

  val read_mapping = collection.mutable.LinkedHashMap[Int,Bits](
    CSRs.mcycle -> reg_time,
    CSRs.minstret -> reg_instret,
    CSRs.mimpid -> 0.U,
    CSRs.marchid -> 0.U,
    CSRs.mvendorid -> 0.U,
    CSRs.misa -> misa.U,
    CSRs.mimpid -> impid.U,
    CSRs.mstatus -> read_mstatus,
    CSRs.mtvec -> MTVEC.U,
    CSRs.mip -> reg_mip.asUInt,
    CSRs.mie -> reg_mie.asUInt,
    CSRs.mscratch -> reg_mscratch,
    CSRs.mepc -> reg_mepc,
    CSRs.mtval -> reg_mtval,
    CSRs.mcause -> reg_mcause,
    CSRs.mhartid -> io.hartid,
    CSRs.dscratch -> reg_dscratch,
    CSRs.medeleg -> reg_medeleg)

  for (i <- 0 until CSR.nCtr)
  {
    read_mapping += (i + CSR.firstMHPC) -> reg_hpmcounter(i)
    read_mapping += (i + CSR.firstMHPCH) -> reg_hpmcounter(i)
  }

  if (conf.xprlen == 32) {
    read_mapping += CSRs.mcycleh -> 0.U //(reg_time >> 32)
    read_mapping += CSRs.minstreth -> 0.U //(reg_instret >> 32)
  }

  val decoded_addr = read_mapping map { case (k, v) => k -> (io.rw.addr === k) }

  val priv_sufficient = reg_mstatus.prv >= io.rw.addr(9,8)
  val read_only = io.rw.addr(11,10).andR
  val cpu_wen = cpu_ren && io.rw.cmd =/= CSR.R && priv_sufficient
  val wen = cpu_wen && !read_only
  val wdata = readModifyWriteCSR(io.rw.cmd, io.rw.rdata, io.rw.wdata)
  
  val opcode = 1.U << io.rw.addr(2,0)
  val insn_call = system_insn && opcode(0)
  val insn_break = system_insn && opcode(1)
  val insn_ret = system_insn && opcode(2) && priv_sufficient
  val insn_wfi = system_insn && opcode(5) && priv_sufficient

  private def decodeAny(m: LinkedHashMap[Int,Bits]): Bool = m.map { case(k: Int, _: Bits) => io.rw.addr === k }.reduce(_||_)
  io.decode.read_illegal := reg_mstatus.prv < io.rw.addr(9,8) || !decodeAny(read_mapping) ||
    (io.rw.addr.inRange(CSR.firstCtr, CSR.firstCtr + CSR.nCtr) || io.rw.addr.inRange(CSR.firstCtrH, CSR.firstCtrH + CSR.nCtr))
  io.decode.write_illegal := io.rw.addr(11,10).andR
  io.decode.system_illegal := reg_mstatus.prv < io.rw.addr(9,8)

  io.status := reg_mstatus

  io.eret := insn_call || insn_break || insn_ret

  assert(PopCount(insn_ret :: io.illegal :: Nil) <= 1, "these conditions must be mutually exclusive")

   when (reg_time >= reg_mtimecmp) {
      reg_mip.mtip := true
   }

  //MRET
  when (insn_ret && !io.rw.addr(10)) {
    reg_mstatus.mie := reg_mstatus.mpie
    reg_mstatus.mpie := true
    new_prv := reg_mstatus.mpp
    io.evec := reg_mepc
  }

  //ECALL 
  when(insn_call){
    io.evec := "h80000004".U
    reg_mcause := reg_mstatus.prv + Causes.user_ecall
    reg_mepc := io.pc
  }

  //EBREAK
  when(insn_break){
    io.evec := "h80000004".U
    reg_mcause := Causes.breakpoint
    reg_mepc := io.pc
  }

  when(io.xcpt) {
    io.evec := "h80000004".U
    reg_mcause := io.cause
    reg_mepc := io.pc
    reg_mtval := io.tval
  }

  io.time := reg_time
  io.csr_stall := reg_wfi


  io.rw.rdata := Mux1H(for ((k, v) <- read_mapping) yield decoded_addr(k) -> v)

  when (wen) {

    when (decoded_addr(CSRs.mstatus)) {
      val new_mstatus = wdata.asTypeOf(new MStatus)
      reg_mstatus.mie := new_mstatus.mie
      reg_mstatus.mpie := new_mstatus.mpie
    }
    when (decoded_addr(CSRs.mip)) {
      val new_mip = wdata.asTypeOf(new MIP)
      reg_mip.msip := new_mip.msip
    }
    when (decoded_addr(CSRs.mie)) {
      val new_mie = wdata.asTypeOf(new MIP)
      reg_mie.msip := new_mie.msip
      reg_mie.mtip := new_mie.mtip
    }
    for (i <- 0 until CSR.nCtr)
    {
      writeCounter(i + CSR.firstMHPC, reg_hpmcounter(i), wdata)
    }
    writeCounter(CSRs.mcycle, reg_time, wdata)
    writeCounter(CSRs.minstret, reg_instret, wdata)

    when (decoded_addr(CSRs.dscratch)) { reg_dscratch := wdata }

    when (decoded_addr(CSRs.mepc))     { reg_mepc := (wdata(conf.xprlen-1,0) >> 2.U) << 2.U }
    when (decoded_addr(CSRs.mscratch)) { reg_mscratch := wdata }
    when (decoded_addr(CSRs.mcause))   { reg_mcause := wdata & ((BigInt(1) << (conf.xprlen-1)) + 31).U /* only implement 5 LSBs and MSB */ }
    when (decoded_addr(CSRs.mtval))    { reg_mtval := wdata(conf.xprlen-1,0) }
    when (decoded_addr(CSRs.medeleg))    { reg_medeleg := wdata(conf.xprlen-1,0) }

  }

  def writeCounter(lo: Int, ctr: WideCounter, wdata: UInt) = {
    val hi = lo + CSRs.mcycleh - CSRs.mcycle
    when (decoded_addr(hi)) { ctr := Cat(wdata(ctr.getWidth-33, 0), ctr(31, 0)) }
    when (decoded_addr(lo)) { ctr := Cat(ctr(ctr.getWidth-1, 32), wdata) }
  }
  def readModifyWriteCSR(cmd: UInt, rdata: UInt, wdata: UInt) =
    (Mux(cmd.isOneOf(CSR.S, CSR.C), rdata, 0.U) | wdata) & ~Mux(cmd === CSR.C, wdata, 0.U)
}