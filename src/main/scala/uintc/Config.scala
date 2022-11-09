package uintc

import chisel3._
import freechips.rocketchip.config._

case object SOCKET_CNT extends Field[Int]

class UINTCConfig extends Config((site, here, up) => {
    case SOCKET_CNT => 8 // number of sockets
})