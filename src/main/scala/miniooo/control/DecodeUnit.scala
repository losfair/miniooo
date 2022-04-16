package miniooo.control

import spinal.core._
import spinal.lib._
import miniooo.util._

case class MaybeArchRegIndex() extends Bundle {
  val valid = Bool()
  val index = Machine.get[MachineSpec].archRegIndexType
}

case class DecodeInfo(inner: HardType[_ <: PolymorphicDataChain]) extends Bundle with PolymorphicDataChain {
  private val spec = Machine.get[MachineSpec]
  val functionUnitTag = spec.functionUnitTagType()
  val archSrcRegs = Vec(MaybeArchRegIndex(), spec.maxNumSrcRegsPerInsn)
  val archDstRegs = Vec(MaybeArchRegIndex(), spec.maxNumDstRegsPerInsn)
  val parentObjects = if(inner != null) Seq(inner()) else Seq()
}
