package uintc

import chisel3._
import freechips.rocketchip.config._
import chisel3.util.log2Ceil

case object SOCKET_CNT extends Field[Int]
case object LG2_SOCKET_CNT extends Field[Int]

class UINTCConfig extends Config((site, here, up) => {
    case SOCKET_CNT => 4 // number of sockets
    case LG2_SOCKET_CNT => log2Ceil(here(SOCKET_CNT)) // log2(SOCKET_CNT)
})