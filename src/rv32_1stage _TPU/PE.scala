package Sodor{

import chisel3._
import chisel3.util._
import Common._
import Common.Instructions._
import Constants._

class PEControl extends Bundle {
  val propagate =Bool() 
  val matmul=Bool()
}

class PE extends Module //TODO:参数设计
{
                  
    val io = IO(new Bundle {
    val weight_in = Input(UInt(8.W))//B矩阵输入
    val din = Input(UInt(8.W))//A矩阵输入
    val psum_in = Input(UInt(16.W))//部分和输入
    val mac_out = Output(UInt(16.W))//计算结果输出
    val dout = Output(UInt(8.W))//A矩阵输出

    val in_control = Input(new PEControl)//控制信号输入
    val out_control = Output(new PEControl)//控制信号输出
  })

   val c=Reg(UInt(8.W))//寄存器存放B矩阵
   val in_control_d=ShiftRegister(io.in_control,1)//将信号延时放在PE中方便同步
   val psum_in_d  = ShiftRegister(io.psum_in, 1)
   val din_d  = ShiftRegister(io.din, 1)
   
   io.dout:=din_d
   io.out_control:=in_control_d

   when(io.in_control.propagate===true.B)
   {
      c:=io.weight_in//如果控制指令时propagate，则在传播B矩阵
   }
   .elsewhen(io.in_control.matmul===true.B)
   {
      io.mac_out:=din_d*c+psum_in_d//如果控制指令时matmul，则进行计算
   }
}
}
