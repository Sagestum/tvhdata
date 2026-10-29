# TVHData

TV-Browser data service that loads the channel list and the EPG from a
[tvheadend](https://tvheadend.org/) server, inspired by
[VDR DataService](https://github.com/hampelratte/VDR-Data-Service) by Henrik Niehaus.
Works well together with [TVHsync](https://github.com/Sagestum/tvhsync).

See [res/docs/README](res/docs/README) for features and installation.

## Build

    ant install

`build.properties` points to the TV-Browser flatpak; adjust `tvbrowser.runtime.dir`
and `plugin.install.dir` for other installations.
