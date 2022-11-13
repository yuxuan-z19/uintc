package uintc

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config

// TODO: to be implemented as LazyModule

class UINTC(implicit p: config.Parameters) extends Module {
    val io = IO(new Bundle {
        val sender_send =
            Flipped(Vec(p(SOCKET_CNT), Decoupled(new Request)))
        val request_disable =
            Input(Vec(p(SOCKET_CNT), Bool())) // maintain by cores
        val recver_claim =
            Vec(p(SOCKET_CNT), Decoupled(UInt(p(LG2_SOCKET_CNT).W)))
        val sender_status = Output(Vec(p(SOCKET_CNT), Bool()))
    })

    val sender = Module(new SenderPort)
    val ctrller = Module(new Controller)
    val recver = Module(new RecverPort)

    sender.io.sender_send <> io.sender_send
    ctrller.io.request_sent <> sender.io.request_sent
    recver.io.request_recv <> ctrller.io.request_recv
    recver.io.request_disable <> io.request_disable
    io.recver_claim <> recver.io.recver_claim
    io.sender_status <> recver.io.sender_status
}

object main extends App {
    val disable_rand = Array(
      "--emission-options",
      "disableMemRandomization,disableRegisterRandomization"
    )
    val emit_to_build = Array(
      "--target-dir",
      "build/"
    )

    implicit val params = (new UINTCConfig).toInstance

    (new chisel3.stage.ChiselStage)
        .emitSystemVerilog(
          new UINTC()(params),
          disable_rand ++ emit_to_build
        )
}
