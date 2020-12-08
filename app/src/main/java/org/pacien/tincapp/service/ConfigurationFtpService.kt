/*
 * Tinc App, an Android binding and user interface for the tinc mesh VPN daemon
 * Copyright (C) 2017-2020 Pacien TRAN-GIRARD
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.pacien.tincapp.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.databinding.ObservableBoolean
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.*
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.pacien.tincapp.R
import org.pacien.tincapp.context.App
import org.pacien.tincapp.extensions.Java.defaultMessage
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * FTP server service allowing a remote and local user to access and modify configuration files in
 * the application's context.
 *
 * @author pacien
 */
class ConfigurationFtpService : Service() {
  companion object {
    const val FTP_PORT = 65521 // tinc port `concat` FTP port
    const val FTP_USERNAME = "tincapp"
    val FTP_HOME_DIR = App.getContext().applicationInfo.dataDir!!
    val FTP_PASSWORD = generateRandomString(8)

    val runningState = ObservableBoolean(false)

    private fun generateRandomString(length: Int): String {
      val alphabet = ('a'..'z') + ('A'..'Z') + ('0'..'9')
      return List(length) { alphabet.random() }.joinToString("")
    }
  }

  private val log by lazy { LoggerFactory.getLogger(this.javaClass)!! }
  private var sftpServer: FtpServer? = null

  override fun onBind(intent: Intent): IBinder? = null // non-bindable service

  override fun onDestroy() {
    sftpServer?.stop()
    sftpServer = null
    runningState.set(false)
    log.info("Stopped FTP server")
    super.onDestroy()
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    val ftpUser = StaticFtpUser(FTP_USERNAME, FTP_PASSWORD, FTP_HOME_DIR, listOf(WritePermission()))
    sftpServer = setupSingleUserServer(ftpUser).also {
      try {
        it.start()
        runningState.set(true)
        log.info("Started FTP server on port {}", FTP_PORT)
      } catch (e: IOException) {
        log.error("Could not start FTP server", e)
        App.alert(R.string.notification_error_title_unable_to_start_ftp_server, e.defaultMessage())
      }
    }

    return START_NOT_STICKY
  }

  private fun setupSingleUserServer(ftpUser: User): FtpServer {
    return FtpServerFactory()
      .apply { addListener("default", ListenerFactory().apply { port = FTP_PORT }.createListener()) }
      .apply { userManager = StaticFtpUserManager(listOf(ftpUser)) }
      .createServer()
  }

  private class StaticFtpUserManager(users: List<User>) : UserManager {
    private val userMap: Map<String, User> = users.map { it.name to it }.toMap()
    override fun getUserByName(username: String?): User? = userMap[username]
    override fun getAllUserNames(): Array<String> = userMap.keys.toTypedArray()
    override fun doesExist(username: String?): Boolean = username in userMap
    override fun delete(username: String?) = throw UnsupportedOperationException()
    override fun save(user: User?) = throw UnsupportedOperationException()
    override fun getAdminName(): String = throw UnsupportedOperationException()
    override fun isAdmin(username: String?): Boolean = throw UnsupportedOperationException()
    override fun authenticate(authentication: Authentication?): User = when (authentication) {
      is UsernamePasswordAuthentication -> getUserByName(authentication.username).let {
        if (it != null && authentication.password == it.password) it
        else throw AuthenticationFailedException()
      }
      else -> throw IllegalArgumentException()
    }
  }

  private data class StaticFtpUser(
    private val name: String,
    private val password: String,
    private val homeDirectory: String,
    private val authorities: List<Authority>
  ) : User {
    override fun getName(): String = name
    override fun getPassword(): String = password
    override fun getAuthorities(): List<Authority> = authorities
    override fun getAuthorities(clazz: Class<out Authority>): List<Authority> = authorities.filter(clazz::isInstance)
    override fun getMaxIdleTime(): Int = 0 // unlimited
    override fun getEnabled(): Boolean = true
    override fun getHomeDirectory(): String = homeDirectory
    override fun authorize(request: AuthorizationRequest?): AuthorizationRequest? =
      authorities.filter { it.canAuthorize(request) }.fold(request) { req, auth -> auth.authorize(req) }
  }
}
