## How To Run The Server

<iframe width="560" height="315" src="https://www.youtube.com/embed/vBP4aawOB4E" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>

**!!! First things first !!!**
If you expose the server to the internet, I highly recommend enabling authentication and secure communication via TLS. Otherwise the whole world has access to your CTB Recorder server.

The archive you downloaded contains a `server.bat` or `server.sh`, which can be used to start the server. On the first start, the server uses a default configuration. Once you terminate the server by pressing <kbd>ctrl</kbd> + <kbd>c</kbd>, the config is stored in your user home.

On Windows that is `C:\Users\{your user name}\AppData\Roaming\ctbrec\server.json`

On Linux it is `~/.config/ctbrec/server.json`

On macOS it is `/Users/{your user name}/Library/Preferences/ctbrec`

You can open this file in a text editor and change it to your likings. You probably only want to change `httpPort` and `recordingsDir`. Most of the other stuff is irrelevant since the server and CTB Recorder use the same config file format. When the server is running, you can connect to it with CTB Recorder by changing the setting "Record location" to "Remote".

### Web Interface
You can enable the server's web interface in the configuration server.json. Just set `webinterface` to true and set values for `webinterfaceUsername` and `webinterfacePassword`.

### SSL / TLS
Since version 2.2.0 CTB Recorder supports SSL / TLS. To switch it on/off you have to change the server and client configuration. On the server side open the server.json and set
`transportLayerSecurity` to true. The server will then open an additional port, which can be configured with `httpSecurePort`.
On the client side open CTB Recorder and on the Settings tab change the port to the value of `httpSecurePort`. Then tick `Use Secure Communication (TLS)`. Afterwards restart
CTB Recorder and you should be able to connect to the server.

By default, CTB Recorder uses a self-signed certificate. If you also use the webinterface, your browser will complain about that, but you can just ignore the warning and add the 
certificate to your trusted certificates.

#### Custom certificate
You can also use your own certificate, if you want to. Follow these steps to create a keystore, which can be used by the server:
* `cd <your_ctbrec_directory>`
* `openssl pkcs12 -export -in <certificate>.pem -inkey <private_key>.pem -out <some_name>.p12 -chain -name <some_name> -CAfile <ca_certificate>.crt -caname root`
* `keytool -v -importkeystore -srckeystore <some_name>.org.p12 -srcstoretype PKCS12 -destkeystore <some_name>.ks -deststoretype PKCS12` (keytool is part of the Java distribution, so you should be able to find it in the bin directory of your Java installation. If not, download the current JDK from jdk.java.net).
You will be asked to enter a password during the process. Enter the same password everytime and note that down.
* Open the server.sh / server.bat
* add `-Dkeystore.file=<some_name>.ks -Dkeystore.password=<your_password>` directly after $JAVA / java

#### Running behind Apache / NGINX 
You can also run the CTB Recorder server behind Apache or NGINX. I only tested it with Apache and mod_proxy:
* disable `transportLayerSecurity` in the server.json, TLS should be handled by Apache 
* set `servletContext` to `/ctbrec`
* in your Apache config add:

	`ProxyPass /ctbrec http://localhost:8080/ctbrec`

	`ProxyPassReverse /ctbrec http://localhost:8080/ctbrec`
* CTB Recorder will then be available under `https://your.server.name/ctbrec`, the webinterface (if enabled) under `https://your.server.name/ctbrec/static/index.html`
* in the CTB Recorder app on the Settings tab enter `your.server.name` for the Server, `443` for the port, `/ctbrec` for the path.
* tick `Use Secure Communication (TLS)`