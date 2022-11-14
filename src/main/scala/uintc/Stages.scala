package uintc

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config

// Ports
class SenderPort(implicit p: config.Parameters) extends Module {
    val io = IO(new Bundle {
        val sender_send =
            Flipped(Vec(p(SOCKET_CNT), Decoupled(new Request)))
        val request_sent = Decoupled(new Request)
    })

    val arbiter = Module(new RRArbiter(new Request, p(SOCKET_CNT)))
    arbiter.io.in <> io.sender_send
    io.request_sent <> arbiter.io.out
}

class RecverPort(implicit p: config.Parameters) extends Module {
    val io = IO(new Bundle {
        val request_recv =
            Flipped(Vec(p(SOCKET_CNT), Decoupled(UInt(p(LG2_SOCKET_CNT).W))))
        val request_disable =
            Input(Vec(p(SOCKET_CNT), Bool())) // maintain by cores
        val recver_claim =
            Vec(p(SOCKET_CNT), Decoupled(UInt(p(LG2_SOCKET_CNT).W)))
        val sender_status = Output(Vec(p(SOCKET_CNT), Bool()))
    })

    (io.recver_claim zip io.request_recv) foreach {
        case (claim, recv) => {
            claim.bits := recv.bits
            claim.valid := recv.valid && io.request_disable(recv.bits) === false.B
            recv.ready := Mux(io.request_disable(recv.bits) === true.B, true.B, claim.ready)
                // if it's disabled just ignore it
        }
    }

    io.sender_status := DontCare
    for (port <- io.recver_claim)
        io.sender_status(port.bits) :=
            RegNext(port.valid && port.ready, init = false.B)
}

// Controller

class EnableMap(implicit p: config.Parameters) extends Module {
    val io = IO(new Bundle {
        val src_request = Flipped(Valid(new Request))
        val sink_request = Valid(new Request)
        val sink_enable = Output(Bool())
    })

    val src_id = io.src_request.bits.idx.src
    val dst_id = io.src_request.bits.idx.dst

    val bitmap = RegInit(
      VecInit(Seq.fill(p(SOCKET_CNT))(0.U((1 << log2Ceil(p(SOCKET_CNT))).W)))
    )
    val bit_set = 1.U << dst_id
    val op = io.src_request.bits.op(1, 0)
    when(io.src_request.valid && !io.src_request.bits.op(2)) {
        when(op === "b01".U) {
            bitmap(src_id) := bitmap(src_id) | bit_set // set
        }.elsewhen(io.src_request.bits.op(1, 0) === "b11".U) {
            bitmap(src_id) := 0.U // clear
        }.elsewhen(op === "b00".U) {
            bitmap(src_id) := bitmap(src_id) & ~bit_set // unset
        }.otherwise {}
    }

    io.sink_request := RegNext(io.src_request)
    io.sink_enable := RegNext(bitmap(src_id)(dst_id))
}

class Controller(implicit p: config.Parameters) extends Module {
    val io = IO(new Bundle {
        val request_sent = Flipped(Decoupled(new Request))
        val request_recv =
            Vec(p(SOCKET_CNT), Decoupled(UInt(p(LG2_SOCKET_CNT).W)))
    })

    val request_queue = Queue(io.request_sent, 16)

    /* enable reg stage */
    val enable_map = Module(new EnableMap)
    enable_map.io.src_request.valid := request_queue.valid
    enable_map.io.src_request.bits := request_queue.bits
    request_queue.ready := enable_map.io.sink_request.valid
    // once the pipereg is set, we could pop the queue

    /* enable reg stage */
    val pending_queues = Seq.fill(p(SOCKET_CNT)) {
        Module(new Queue(UInt(p(LG2_SOCKET_CNT).W), 16))
    }
    // ? act as adjacency list

    val request = enable_map.io.sink_request.bits
    pending_queues.zipWithIndex.foreach {
        case (q, idx) => {
            q.io.enq.bits := request.idx.src
            when(
                enable_map.io.sink_request.valid && enable_map.io.sink_enable &&
                request.idx.dst === idx.U
            ) {
                q.io.enq.valid := enable_map.io.sink_request.valid && request.op(2)
            }.otherwise { q.io.enq.valid := false.B }
            io.request_recv(idx) <> q.io.deq
        }
    }
}
