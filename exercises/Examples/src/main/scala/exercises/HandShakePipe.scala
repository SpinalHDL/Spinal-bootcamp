// https://www.itdev.co.uk/blog/pipelining-axi-buses-registered-ready-signals

package exercises

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.sim._

class HandShakePipe(width: Int) extends Component {
  val io = new Bundle {
    val input = new Bundle {
      val valid = in Bool
      val payload = in UInt (width bits)
      val ready = out Bool
    }

    val output = new Bundle {
      val valid = out Bool
      val payload = out UInt (width bits)
      val ready = in Bool
    }
  }
}

class M2SHandShakePipe(
    width: Int,
    noBubble: Boolean = true
) extends HandShakePipe(width) {
  val doutReg = Reg(UInt(width bits)) init (0)
  val validReg = Reg(Bool) init (False)
  io.output.payload := doutReg
  io.output.valid := validReg

  if (noBubble) {
    when(io.input.ready) {
      doutReg := io.input.payload
      validReg := io.input.valid
    }

    io.input.ready := io.output.ready || !validReg
  } else {
    when(io.input.ready) {
      doutReg := io.input.payload
      validReg := io.input.valid
    }

    io.input.ready := io.output.ready
  }
}

class S2MHandShakePipe(
    width: Int,
    noBubble: Boolean = true
) extends HandShakePipe(width) {
  val readyReg = RegNext(io.output.ready) init (False)
  val doutReg = Reg(UInt(width bits)) init (0)
  val validReg = Reg(Bool) init (False)

  if (noBubble) {
    io.output.valid := io.input.valid || validReg
    io.output.payload := validReg ? doutReg | io.input.payload

    io.input.ready := ~validReg

    when(io.output.ready) {
      validReg := False
    }

    when(io.input.ready && ~io.output.ready) {
      doutReg := io.input.payload
      validReg := io.input.valid
    }
  } else {
    io.input.ready := readyReg
    when(io.output.ready && !readyReg) {
      io.output.payload := doutReg
      io.output.valid := validReg
    }.elsewhen(io.output.ready && readyReg) {
      io.output.payload := io.input.payload
      io.output.valid := io.input.valid
    }.elsewhen(!io.output.ready && readyReg) {
      io.output.payload := io.input.payload
      io.output.valid := io.input.valid
    }.otherwise { // !io.output.ready && !readyReg
      io.output.payload := io.input.payload
      io.output.valid := io.input.valid
    }
  }
}

class BothHandShakePipe(
    width: Int
) extends HandShakePipe(width) {
  val primDataReg = Reg(UInt(width bits)) init (0)
  val primVldReg = Reg(Bool) init (False)

  val sideDataReg = Reg(UInt(width bits)) init (0)
  val sideVldReg = Reg(Bool) init (False)

  io.input.ready := ~sideVldReg
  io.output.valid := primVldReg || sideVldReg
  io.output.payload := sideVldReg ? sideDataReg | primDataReg

  when(io.output.ready) {
    sideVldReg := False
  }

  when(io.input.ready) {
    primDataReg := io.input.payload
    primVldReg := io.input.valid

    when(~io.output.ready) {
      sideDataReg := primDataReg
      sideVldReg := primVldReg
    }
  }
}

class BothHandShakePipe2(
    width: Int
) extends HandShakePipe(width) {
  val firstDataReg = Reg(UInt(width bits)) init (0)
  val firstVldReg = Reg(Bool) init (False)

  val interStream = Stream(UInt(width bits))

  val secondDataReg = Reg(UInt(width bits)) init (0)
  val secondVldReg = Reg(Bool) init (False)

  val s2mArea = new Area {
    io.input.ready := ~firstVldReg
    interStream.valid := io.input.valid || firstVldReg
    interStream.payload := firstVldReg ? firstDataReg | io.input.payload

    when(interStream.ready) {
      firstVldReg := False
    }

    when(io.input.ready && ~interStream.ready) {
      firstDataReg := io.input.payload
      firstVldReg := io.input.valid
    }
  }

  val m2sArea = new Area {
    io.output.valid := secondVldReg
    io.output.payload := secondDataReg
    interStream.ready := io.output.ready || ~secondVldReg

    when(interStream.ready) {
      secondDataReg := interStream.payload
      secondVldReg := interStream.valid
    }
  }
}
