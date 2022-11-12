package uintc

import chipsalliance.rocketchip.config
import chisel3._
import chisel3.util._

// TODO: establish a LUT for uiid 2 socket

class SendRecvBitmap(socketCount: Int) extends Module {
    val io = IO(new Bundle {
        val src_idx = Flipped(Valid(new Index(socketCount)))
        val src_query = Flipped(Valid(UInt(3.W)))
        val sink_idx = (Valid(new Index(socketCount)))
        val sink_query = Output(UInt(3.W))
        val query_succeed = Output(Bool())
        val is_available = Output(Bool())
        val has_pending = Output(Bool())
    })

    /* query[1:0]: 00/01 do nth, 10 unset bit, 11 set bit */

    val is_valid = io.src_idx.valid // current query/modify is valid
    val src_id = io.src_idx.bits.src
    val dst_id = io.src_idx.bits.dst
    val is_modify = io.src_query.bits(1)

    val bitmap = RegInit(
      VecInit(Seq.fill(socketCount)(0.U((1 << log2Ceil(socketCount)).W)))
    )
    val bit_set = 1.U << src_id
    when(is_valid && io.src_query.valid && is_modify) {
        when(io.src_query.bits(0)) {
            bitmap(dst_id) := bitmap(dst_id) | bit_set // set
        }.otherwise {
            bitmap(dst_id) := bitmap(dst_id) & ~bit_set // unset
        }
    }

    io.query_succeed := RegNext(io.src_query.valid) // FIXME: modify this while integrating into labeled
    io.is_available := RegNext(bitmap(dst_id)(src_id))
    io.has_pending := RegNext(bitmap(dst_id).orR)

    // setting the pipereg
    io.sink_idx := RegNext(io.src_idx)
    io.sink_query := RegNext(io.src_query.bits)
}

/*
    TODO: use a FIFO to sync requests from multiple cores (refer to ControlUnitTestHarness)
 */

class ControlUnit(implicit p: config.Parameters) extends Module {
    val cnt = p(SOCKET_CNT)
    val io = IO(new Bundle {
        val info_polled = Flipped(Decoupled(new Info)) // to FIFO
        val send_succeed = Valid(UInt(log2Ceil(cnt).W)) // notify SenderUnit to set status
        val has_pending = Output(Bool()) // ! must flow out
    })

    val enable = Module(new SendRecvBitmap(cnt))
    enable.io.src_idx.bits := io.info_polled.bits.idx
    enable.io.src_idx.valid := io.info_polled.valid
    enable.io.src_query.bits := io.info_polled.bits.op
    enable.io.src_query.valid := !io.info_polled.bits.op(2)

    io.info_polled.ready := enable.io.sink_idx.valid

    val pending = Module(new SendRecvBitmap(cnt))
    pending.io.src_idx <> enable.io.sink_idx
    pending.io.src_query.bits := enable.io.sink_query
    pending.io.src_query.valid := enable.io.sink_query(2) && enable.io.is_available
    io.has_pending := pending.io.has_pending

    io.send_succeed.valid := pending.io.sink_idx.valid
    io.send_succeed.bits := pending.io.sink_idx.bits.src
}