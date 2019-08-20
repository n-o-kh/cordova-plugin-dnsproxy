# cordova-plugin-dnsproxy

# Installation

From github latest

`cordova plugin add https://github.com/RoqosInc/cordova-plugin-dnsproxy`

# Supported Platform
- Android

# Methods

## DnsProxy.config
Config the DNS Server

    window.plugins.dnsProxy.config({
        dnsServer: "dnsServer",
        port: "as a string",
        VPNSessionTitle: "title to show in the session pop up"
    });

#### Quick Example
    window.plugins.dnsProxy.config({
        dnsServer: "124.15.25.65",
        port: "53",
        VPNSessionTitle: "Roqos"
    });

## DnsProxy.isActivated
Check the activation of the dns configuration

    window.plugins.dnsProxy.isActivated(success, error);

#### Quick Example
    window.plugins.dnsProxy.isActivated(function(status){
        console.log(status);
    });

## DnsProxy.activate
Activate the custom dns configuration

    window.plugins.dnsProxy.activate(success, error);

#### Quick Example
    window.plugins.dnsProxy.activate();

## DnsProxy.deactivate
Deactivate the custom dns configuration

    window.plugins.dnsProxy.deactivate(success, error);

#### Quick Example
    window.plugins.dnsProxy.deactivate();

## DnsProxy.addEDNSOption
Add an EDNS option.

    window.plugins.dnsProxy.addEDNSOption(optionCode, message, success, error);

#### Quick Example
    window.plugins.dnsProxy.addEDNSOption(65073, "545e5f");

## DnsProxy.removeAllEDNSOption
remove all the added EDS Options

    window.plugins.dnsProxy.removeAllEDNSOption(success, error);

#### Quick Example
    window.plugins.dnsProxy.removeAllEDNSOption();
    
License
------------

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
