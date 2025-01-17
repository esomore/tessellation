package org.tesselation.domain.cluster.storage

import org.tesselation.schema.cluster.SessionToken

trait SessionStorage[F[_]] {
  def createToken: F[SessionToken]
  def getToken: F[Option[SessionToken]]
  def clearToken: F[Unit]
}
