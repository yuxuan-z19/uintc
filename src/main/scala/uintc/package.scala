import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config

package object uintc {
    class Index(implicit p: config.Parameters) extends Bundle {
        val src = UInt(p(LG2_SOCKET_CNT).W)
        val dst = UInt(p(LG2_SOCKET_CNT).W)
    }

    class Request(implicit p: config.Parameters) extends Bundle {
        val idx = new Index
        val op = UInt(3.W) // 0xx set enable, 1xx set pending
    }
}
