# Help

### Emit SystemVerilog code

```scala
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
          new ControlUnit()(params),
          disable_rand ++ emit_to_build
        )
}
```

### Notes

#### 由操作系统维护的寄存器在rocket core中实现

1. 发送方和设置`sender_uiid`，接收方设置`receiver_uiid`，实际上这两个`uiid`可以简化为一个
2. 发送方在ID阶段生成如下的信息组，在MEM/WB阶段交给中断总线上，中断总线上也维护了一个待响应UINT队列`uint_queue`
    
    ```scala
    class Info(implicit p: config.Parameters) extends Bundle {
        val send_uiid = UInt(p(LG2_SOCKET_CNT).W) 
        val recv_uiid = UInt(p(LG2_SOCKET_CNT).W)
        val op = UInt(3.W)
    }
    ```

    操作码规范如下：
    |op[2]|op[1:0]|
    |:-:|:-:|
    |`0`设置enable, `1`设置pending|`00`取消设置, `01`设置, `11`清空|

    采用握手信号和冗余位来保证安全性

    > 清空请求发生 $\Leftrightarrow$ 切换上下文 $\Rightarrow$ 清空当前的`enable`和`pending`，因此**特别约定**当低2位全为1时清空`enable`和`pending`

3. UINTC控制器通过地址映射的方式为每个核维护独立的`pending`队列
4. `receiver_claim`取出后会弹出`pending`队列，同时反向设置`sender_status`
5. 在核内还有一个寄存器来`status_enable`的寄存器，如果为`true.B`则接收`sender_status`，否则发送`bX11.U`