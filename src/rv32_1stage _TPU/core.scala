//**************************************************************************
// RISCV Processor 
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 Jul 30
//
// Describes a simple RISCV 1-stage processor
//   - No div/mul/rem
//   - No FPU
//   - implements a minimal supervisor mode (can trap to handle the
//       above instructions)
//
// The goal of the 1-stage is to provide the simpliest, easiest-to-read code to
// demonstrate the RISC-V ISA.
 
package Sodor
{

import chisel3._
import Common.{SodorConfiguration, MemPortIo}
import javax.management.modelmbean.ModelMBean

class CoreIo(implicit val conf: SodorConfiguration) extends Bundle 
{
  val imem = new MemPortIo(conf.xprlen)
  val dmem = new MemPortIo(conf.xprlen)
}

class Core(implicit conf: SodorConfiguration) extends Module
{
  val io = IO(new CoreIo())
  io := DontCare
  val c  = Module(new CtlPath())
  val d  = Module(new DatPath())
  val m  = Module(new TPUcontroler())
  val t  = Module(new Tile)

  c.io.ctl  <> d.io.ctl
  c.io.dat  <> d.io.dat

  d.io.tpu_ctl  <> m.io.dpathio
  t.io.ctlio <> m.io.tpathio
  
  io.imem <> c.io.imem
  io.imem <> d.io.imem
  
  io.dmem <> c.io.dmem
  io.dmem <> d.io.dmem
  io.dmem <> m.io.dmem

  io.dmem.req.valid := c.io.dmem.req.valid
  io.dmem.req.bits.typ := c.io.dmem.req.bits.typ
  io.dmem.req.bits.fcn := c.io.dmem.req.bits.fcn

  io.dmem.req.bits.typ := m.io.dmem.req.bits.typ
  io.dmem.req.bits.fcn := m.io.dmem.req.bits.fcn

  io.dmem.req.bits.typ := m.io.dmem.req.bits.typ
  io.dmem.req.bits.fcn := m.io.dmem.req.bits.fcn
}

}
