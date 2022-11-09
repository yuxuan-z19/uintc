package uintc

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.util.Decoupled
import chisel3.util.Queue
import chipsalliance.rocketchip.config

class ControlUnitTestHarness(implicit p: config.Parameters) extends Module {
    val io = IO(new Bundle {
        val info_in = Flipped(Decoupled(new Info))
        val has_pending = Output(Bool()) // ! must flow out
    })
    val queue = Queue(io.info_in, 5)
    val ctrl = Module(new ControlUnit())
    ctrl.io.info_polled <> queue
    io.has_pending := ctrl.io.has_pending
}

class ControlUnitSpec extends AnyFreeSpec with ChiselScalatestTester {
    "ControlUnit should have SendRecvBitmap working properly" in {
        test(new SendRecvBitmap(5)) { d =>
            // time slice #0 : set
            d.io.src_idx.bits.src.poke(1.U)
            d.io.src_idx.bits.dst.poke(3.U)
            d.io.src_query.bits.poke("b11".U)
            d.io.src_idx.valid.poke(true.B) // keep unchanged
            d.io.src_query.valid.poke(true.B) // keep unchanged
            d.clock.step(1)
            d.io.sink_idx.bits.dst.expect(3.U)
            d.io.sink_idx.bits.src.expect(1.U)
            d.io.sink_query.expect("b11".U)
            d.io.is_available.expect(false.B)
            d.io.has_pending.expect(false.B)

            // time slice #1 : set
            d.io.src_idx.bits.src.poke(3.U)
            d.io.src_idx.bits.dst.poke(2.U)
            d.io.src_query.bits.poke("b11".U)
            d.clock.step(1)
            d.io.sink_idx.bits.dst.expect(2.U)
            d.io.sink_idx.bits.src.expect(3.U)
            d.io.sink_query.expect("b11".U)
            d.io.is_available.expect(false.B)
            d.io.has_pending.expect(false.B)

            // time slice #2 : query
            d.io.src_idx.bits.src.poke(1.U)
            d.io.src_idx.bits.dst.poke(3.U)
            d.io.src_query.bits.poke("b00".U)
            d.clock.step(1)
            d.io.is_available.expect(true.B)
            d.io.has_pending.expect(true.B)

            // time slice #3 : unset
            d.io.src_idx.bits.src.poke(3.U)
            d.io.src_idx.bits.dst.poke(2.U)
            d.io.src_query.bits.poke("b10".U)
            d.clock.step(1)
            d.io.is_available.expect(true.B)
            d.io.has_pending.expect(true.B)

            // time slice #4, $5 : query (just unset)
            d.io.src_query.bits.poke("b01".U)
            d.clock.step(1)
            d.io.is_available.expect(false.B)
            d.io.has_pending.expect(false.B)
            d.clock.step(1)
            d.io.is_available.expect(false.B)
            d.io.has_pending.expect(false.B)
        }
    }

    "ControlUnit should handle the request in a FIFO manner" in {
        implicit val p = (new UINTCConfig).toInstance
        test(new ControlUnitTestHarness()(p))
            .withAnnotations(Seq(WriteVcdAnnotation)) { d =>
                d.io.info_in.valid.poke(true.B)
                d.io.info_in.bits.idx.src.poke(1.U)
                d.io.info_in.bits.idx.dst.poke(4.U)
                d.io.info_in.bits.op.poke("b111".U)

                d.clock.step(1)

                d.io.info_in.bits.idx.src.poke(1.U)
                d.io.info_in.bits.idx.dst.poke(4.U)
                d.io.info_in.bits.op.poke("b011".U)

                d.clock.step(1)

                d.io.info_in.bits.idx.src.poke(3.U)
                d.io.info_in.bits.idx.dst.poke(2.U)
                d.io.info_in.bits.op.poke("b011".U)

                d.clock.step(1)

                d.io.info_in.bits.idx.src.poke(3.U)
                d.io.info_in.bits.idx.dst.poke(2.U)
                d.io.info_in.bits.op.poke("b111".U)

                d.clock.step(1)

                d.io.info_in.bits.idx.src.poke(3.U)
                d.io.info_in.bits.idx.dst.poke(2.U)
                d.io.info_in.bits.op.poke("b110".U)
                
                d.clock.step(5)
            }
    }
}
