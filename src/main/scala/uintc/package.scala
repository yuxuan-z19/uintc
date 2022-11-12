import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config

package object uintc {
    class Index(socketCount: Int) extends Bundle {
        val src = UInt(log2Ceil(socketCount).W)
        val dst = UInt(log2Ceil(socketCount).W)
    }

    class Info(implicit p: config.Parameters) extends Bundle {
        val idx = new Index(p(SOCKET_CNT))
        val op = UInt(3.W) // 0xx enable 1xx pending
    }
}
