package mosaic

import firrtl.options.{StageMain}

object Generator extends StageMain(new stage.MoSAICStage)
