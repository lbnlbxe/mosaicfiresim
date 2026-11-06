// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package mosaic.stage

import firrtl.options.Shell

trait MoSAICCli { this: Shell =>

  parser.note("MoSAIC Generator Options")
  Seq(
    TopModuleAnnotation,
    OutputBaseNameAnnotation,
    LegacySFCAnnotation
  ).foreach(_.addOptions(parser))
}
