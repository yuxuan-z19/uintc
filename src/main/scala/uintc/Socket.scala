package uintc

import chipsalliance.rocketchip.config
import chisel3._
import chisel3.util._

class SenderSocket(implicit p: config.Parameters) extends Module {
    val io = IO(new Bundle {
        val send =
            Flipped(Vec(p(SOCKET_CNT), Decoupled(UInt(p(LG2_SOCKET_CNT).W))))
        // ? sender queues maintained by each process
        val info_polled = Decoupled(new Info)
    })
    val arbiter = Module(
      new RRArbiter(UInt(p(LG2_SOCKET_CNT).W), p(SOCKET_CNT))
    )
    arbiter.io.in <> io.send

    val info_emplaced = Wire(new Info)
    info_emplaced.idx.dst := arbiter.io.out.bits
    info_emplaced.idx.src := arbiter.io.chosen
    info_emplaced.op := "b111".U // TODO: add more ops, at the moment just send interrupt

    val queue = Module(new Queue(new Info, entries = 10))
    queue.io.enq.bits := info_emplaced
    queue.io.enq.valid := arbiter.io.out.valid
    arbiter.io.out.ready := queue.io.enq.ready
    io.info_polled <> queue.io.deq
}
