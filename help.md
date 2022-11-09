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