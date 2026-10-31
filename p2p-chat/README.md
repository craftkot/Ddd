# EHTID P2P Chat — Android prototype

## How it works
Two Android devices running the app on **the same Wi-Fi LAN** communicate over
a direct TCP socket. Android NSD/mDNS advertises the first device automatically.

1. Install the same APK on both phones and connect to the same Wi-Fi.
2. Launch on phone A and send a message. It opens a server socket and advertises a service.
3. Launch on phone B. It detects phone A and joins automatically; phone A transfers up to 100 messages.
4. Both phones can send and receive text messages while the app is open.

Messages are stored locally in Android SharedPreferences. There is no cloud server,
account, Firebase, tracker, or backend. Use only on trusted Wi-Fi: **messages are
not encrypted or authenticated**; any person on the LAN running the app can join.
Some guest Wi-Fi and hotspot configurations block peer traffic or mDNS.
A host must keep the app open; this prototype does not run a foreground service.
Messages are not queued for offline peers. Network delivery is not end-to-end confirmed.

Different mobile networks / public Internet require rendezvous + ICE/STUN/TURN or
another NAT traversal method. This APK does not pretend to offer internet P2P.

## Build
JDK 17, Android SDK platform 35, Gradle 8.9:
`gradle :app:assembleDebug`

CI builds an installable debug-signed APK in Actions artifacts on branch
`feature/p2p-chat`. Main branch of RelayBox is unchanged.
