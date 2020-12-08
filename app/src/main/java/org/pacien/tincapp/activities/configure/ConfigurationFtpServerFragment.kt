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

package org.pacien.tincapp.activities.configure

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.Observable
import androidx.databinding.ObservableBoolean
import org.pacien.tincapp.activities.BaseFragment
import org.pacien.tincapp.databinding.ConfigureToolsConfigurationFtpServerFragmentBinding
import org.pacien.tincapp.service.ConfigurationFtpService

/**
 * @author pacien
 */
class ConfigurationFtpServerFragment : BaseFragment() {
  private val ftpServerStartListener = object : Observable.OnPropertyChangedCallback() {
    override fun onPropertyChanged(sender: Observable, propertyId: Int) {
      binding.ftpEnabled = (sender as ObservableBoolean).get()
    }
  }

  private lateinit var binding: ConfigureToolsConfigurationFtpServerFragmentBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = ConfigureToolsConfigurationFtpServerFragmentBinding.inflate(inflater, container, false)
    binding.ftpUsername = ConfigurationFtpService.FTP_USERNAME
    binding.ftpPassword = ConfigurationFtpService.FTP_PASSWORD
    binding.ftpPort = ConfigurationFtpService.FTP_PORT
    binding.toggleFtpState = { toggleServer() }
    return binding.root
  }

  override fun onResume() {
    super.onResume()
    ConfigurationFtpService.runningState.addOnPropertyChangedCallback(ftpServerStartListener)
    binding.ftpEnabled = ConfigurationFtpService.runningState.get()
  }

  override fun onPause() {
    ConfigurationFtpService.runningState.removeOnPropertyChangedCallback(ftpServerStartListener)
    super.onPause()
  }

  private fun toggleServer() {
    val targetServiceIntent = Intent(requireContext(), ConfigurationFtpService::class.java)

    if (binding.ftpEnabled)
      requireContext().stopService(targetServiceIntent)
    else
      requireContext().startService(targetServiceIntent)
  }
}
