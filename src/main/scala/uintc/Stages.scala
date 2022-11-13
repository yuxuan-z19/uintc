package uintc

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config

class SenderPort(implicit p: config.Parameters) extends Module {
    val io = IO(new Bundle {
        val sender_uiid = Input(Vec(p(SOCKET_CNT), UInt(p(LG2_SOCKET_CNT).W)))
        val sender_send =
            Flipped(Vec(p(SOCKET_CNT), Decoupled(UInt(p(LG2_SOCKET_CNT).W))))
        val interrupt_generated = Decoupled(new Info)
    })

    val arbiter = Module(
      new RRArbiter(UInt(p(LG2_SOCKET_CNT).W), p(SOCKET_CNT))
    )
    arbiter.io.in <> io.sender_send

    io.interrupt_generated.bits.idx.dst := arbiter.io.out.bits
    io.interrupt_generated.bits.idx.src := io.sender_uiid(arbiter.io.chosen)
    io.interrupt_generated.bits.op := "b111".U
    io.interrupt_generated.valid := arbiter.io.out.valid
    arbiter.io.out.ready := io.interrupt_generated.ready
}

class RecverPort(implicit p: config.Parameters) extends Module {
    val io = IO(new Bundle {
        val recver_uiid = Input(Vec(p(SOCKET_CNT), UInt(p(LG2_SOCKET_CNT).W)))
        val interrupt_handled =
            Flipped(Vec(p(SOCKET_CNT), Decoupled(UInt(p(LG2_SOCKET_CNT).W))))
        val recver_claim =
            Vec(p(SOCKET_CNT), Decoupled(UInt(p(LG2_SOCKET_CNT).W)))
        val sender_status = Output(Vec(p(SOCKET_CNT), Bool()))
    })

    io.recver_claim <> io.interrupt_handled

    io.sender_status := DontCare
    for (port <- io.recver_claim)
        io.sender_status(port.bits) :=
            RegNext(port.valid && port.ready, init = false.B)
}
