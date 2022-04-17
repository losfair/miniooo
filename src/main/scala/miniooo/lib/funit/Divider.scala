package miniooo.lib.funit

import spinal.core._
import spinal.lib._
import miniooo.util.PolymorphicDataChain
import miniooo.control._
import spinal.lib.fsm._

case class DividerOperation() extends Bundle with PolymorphicDataChain {
  def parentObjects = Seq()

  val useRemainder = Bool()
  val signed = Bool()
}

final case class Divider(staticTag: Data) extends FunctionUnit {
  override def lowLatency = false

  override def generate(
      hardType: HardType[_ <: PolymorphicDataChain]
  ): FunctionUnitInstance = {
    new FunctionUnitInstance {
      private val spec = Machine.get[MachineSpec]

      case class RtCtx() extends Bundle {
        val op = DividerOperation()
        val aNeg = Bool()
        val bNeg = Bool()
        val dividend = spec.dataType
        val divisor = spec.dataType
        val quotient = spec.dataType
        val remainder = spec.dataType
        val counter = UInt(log2Up(spec.dataWidth.value) bits)
        val robIndex = spec.robEntryIndexType()
      }

      val io_available = Bool()
      val io_input = Stream(hardType())
      val io_output = Stream(CommitRequest(null))

      io_available := False
      io_input.setBlocked()
      io_output.setIdle()

      val rt = Reg(RtCtx())

      val fsm = new StateMachine {
        val init: State = new State with EntryPoint {
          whenIsActive {
            io_available := True
            io_input.ready := True
            when(io_input.valid) {
              val dispatchInfo = io_input.payload.lookup[DispatchInfo]
              val issue = io_input.payload.lookup[IssuePort[_]]

              val newRt = RtCtx()
              newRt.op := io_input.payload.lookup[DividerOperation]
              newRt.aNeg := issue.srcRegData(0)(31)
              newRt.bNeg := issue.srcRegData(1)(31)
              newRt.dividend := issue.srcRegData(0)
              newRt.divisor := issue.srcRegData(1)
              newRt.quotient := 0
              newRt.remainder := 0
              newRt.counter := 0
              newRt.robIndex := dispatchInfo.robIndex
              Machine.report(Seq("begin division: ", issue.srcRegData(0), " ", issue.srcRegData(1)))
              rt := newRt
              goto(work)
            }
          }
        }

        val work: State = new State {
          whenIsActive {
            val preRemainder = rt.remainder(
              spec.dataWidth.value - 2 downto 0
            ) ## rt.dividend(spec.dataWidth.value - 1)
            val dividend =
              rt.dividend(spec.dataWidth.value - 2 downto 0) ## B(0, 1 bits)
            val remainderOverflow = preRemainder.asUInt >= rt.divisor.asUInt
            val remainder = remainderOverflow.mux(
              True -> (preRemainder.asUInt - rt.divisor.asUInt).asBits,
              False -> preRemainder
            )
            val quotient = remainderOverflow.mux(
              True -> (rt
                .quotient(spec.dataWidth.value - 2 downto 0) ## B(1, 1 bits)),
              False -> (rt
                .quotient(spec.dataWidth.value - 2 downto 0) ## B(0, 1 bits))
            )
            rt.dividend := dividend
            rt.quotient := quotient
            rt.remainder := remainder
            rt.counter := rt.counter + 1

            when(rt.counter === rt.counter.maxValue) {
              goto(complete)
            }
          }
        }
        val complete: State = new State {
          whenIsActive {
            val quotient = rt.quotient.asUInt
              .twoComplement(rt.op.signed & (rt.aNeg ^ rt.bNeg))
              .asBits
            val remainder =
              rt.remainder.asUInt.twoComplement(rt.op.signed & rt.aNeg).asBits
            val out = rt.op.useRemainder
              .mux(
                True -> remainder,
                False -> quotient
              )
              .resize(spec.dataWidth)
            io_output.valid := True
            io_output.payload.robAddr := rt.robIndex
            io_output.payload.regWriteValue(0) := out
            when(io_output.ready) {
              Machine.report(Seq("end division: ", out))
              goto(init)
            }
          }
        }
      }

    }
  }
}
