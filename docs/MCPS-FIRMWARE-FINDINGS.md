# MCPS firmware findings

Investigation date: 2026-11-16

This note records source-level findings from the upstream MeshCore repository. It is not a claim that MCPS is implemented.

## Companion-radio request handling

In upstream `examples/companion_radio/MyMesh.cpp`, `MyMesh::onContactRequest` handles the standard telemetry request. For other request types, it delegates to `Listener::onUnhandledRequest` when a listener is registered.

In `examples/companion_radio/MyMesh.h`, the default implementation of `Listener::onUnhandledRequest` returns 0, which means no reply. The companion firmware therefore has an explicit extension point for a custom request type without changing the core radio packet format.

The default companion UI listener classes do not declare an MCPS handler. A custom firmware build would need to implement the callback (or another deliberate handler) and register that listener.

## Request-code collision check

The upstream `examples/simple_repeater/MyMesh.cpp` currently defines:
- `0x01` GET_STATUS
- `0x02` KEEP_ALIVE
- `0x03` GET_TELEMETRY_DATA
- `0x05` GET_ACCESS_LIST
- `0x06` GET_NEIGHBOURS
- `0x07` GET_OWNER_INFO

These are repeater request codes, and must not be reused casually. The companion-radio's own `onContactRequest` currently handles telemetry and delegates other request types to its listener.

MCPS must choose a code only after checking all request-code definitions and the exact firmware fork/build that will run on the XIAO devices. Both peers need compatible firmware for automatic request/reply exchange.

## App response identity limitation

The app's current `DeviceEvent.BinaryResponse` contains the raw binary-response payload. `MeshCoreEngine.binaryRequest` matches the echoed request tag, but the event itself does not carry the full sender public key.

A tag is a correlation value, not an identity or authentication proof. The app must not attach a received profile to a contact using the tag alone. Before implementing automatic persistence, extend the response plumbing so the originating contact identity is preserved from the authenticated/decrypted packet, then require it to equal the expected full public key.

## Practical consequence

The most promising architecture is:
- custom companion-radio listener: receive and answer a versioned MCPS request;
- custom Android app: initiate requests after a verified advert, correlate the reply, and store the returned profile separately from local profile fields.

The first iteration should exchange only small, opted-in text fields. Avatar transfer should remain a later feature.
