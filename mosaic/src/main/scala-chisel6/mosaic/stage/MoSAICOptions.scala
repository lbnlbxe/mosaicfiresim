// See LICENSE

package mosaic.stage

class MoSAICOptions private[stage] (
                                         val topModule:               Option[Class[_ <: Any]] = None,
                                         val outputBaseName:          Option[String] = None,
                                         val enableSFCFIRRTLEmission: Boolean = false) {

  private[stage] def copy(
                           topModule:               Option[Class[_ <: Any]] = topModule,
                           outputBaseName:          Option[String] = outputBaseName,
                           enableSFCFIRRTLEmission: Boolean = enableSFCFIRRTLEmission,
                         ): MoSAICOptions = {

    new MoSAICOptions(
      topModule=topModule,
      outputBaseName=outputBaseName,
      enableSFCFIRRTLEmission=enableSFCFIRRTLEmission,
    )
  }

  lazy val topPackage: Option[String] = topModule match {
    case Some(a) => Some(a.getPackage.getName)
    case _ => None
  }

  lazy val longName: Option[String] = outputBaseName match {
    case Some(name) => Some(name)
    case _ =>
      if (!topPackage.isEmpty) Some(s"${topPackage.get}") else None
  }
}
