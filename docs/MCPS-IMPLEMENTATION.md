# MCPS — MeshCore Custom Profile Sync

Status: protocol investigation / implementation gate. This document does not claim that profile sync is implemented.

## Goal

Exchange selected MeshCore Custom contact-profile fields automatically between two opted-in MeshCore Custom users, without creating a chat message or showing protocol data in the conversation.

The first version should exchange only small text fields:
- display name / custom nickname;
- short public description;
- optional Discord username or validated Discord link.

Avatars are out of scope for the first radio exchange. They should be handled later as an explicit, size-limited transfer or a user-approved external reference. Private notes, local labels, favourites, and other locally managed fields must never be transmitted.

## Confirmed app-side integration points

- `MeshCoreEngine` emits `MeshEvent.VerifiedAdvertHeard` after the advert signature has been verified.
- The event contains the parsed advert and advert payload. This is a useful discovery trigger, but the app must resolve and retain the full public key before associating any received profile with a contact.
- `MeshCoreEngine.sendDirectMessage` uses the normal text-message path. It must not be used for MCPS because ordinary MeshCore clients would display the payload as chat.
- `Frames.sendControlData` currently builds the existing control/discovery command. The checked-in protocol documentation lists discovery request/response subtypes only; inventing another control subtype without firmware support is unsafe.
- `Frames.sendBinaryRequest` already builds the standard `CMD_SEND_BINARY_REQ` command, and the engine has a `PUSH_CODE_BINARY_RESPONSE` event path. The companion protocol describes this as an application binary request/response, not a chat message.

## Important compatibility gate

The companion-radio command only transports a binary request. It does not, by itself, define an MCPS request type or make stock firmware understand one.

Before enabling MCPS transmission, the MeshCore firmware used by both radios must be checked for:
1. a documented, non-conflicting request type reserved for application extensions, or a deliberate custom firmware handler;
2. bounded request and response sizes;
3. a response path that preserves enough sender identity to match the reply to the expected full public key;
4. safe behaviour when the peer does not support MCPS.

If no suitable request type exists, add a small, explicitly versioned handler to the user's custom firmware fork as a separate change. Do not assign an arbitrary request code in the Android app and assume stock firmware will process it.

## Proposed first-version flow

1. The user explicitly enables **Share my profile with compatible MeshCore Custom users**.
2. The user selects each public field to share. All fields default to off until selected.
3. A newly heard, signature-verified advert is resolved to a full public key and deduplicated.
4. The app sends a small MCPS capability/profile request only if local sharing is enabled, the contact is eligible, and the peer has not recently been queried.
5. A compatible peer replies with a versioned, bounded profile payload. An unsupported peer remains silent and ordinary MeshCore behaviour is unchanged.
6. The app validates the response, checks it belongs to the expected peer, and stores it as **unverified profile claims from that public key**. It must not silently replace the user's local nickname or other locally edited fields.
7. Duplicate requests are suppressed with a per-peer cooldown and a payload revision/hash.

## Wire-format requirements (not yet assigned numeric request codes)

The application payload should contain:
- a fixed MCPS magic value;
- protocol version;
- operation (capability query, profile response, optional future avatar-reference operation);
- a short request nonce / correlation value;
- a bounded field bitmap;
- UTF-8 values with strict per-field and total byte limits.

The implementation must reject unknown versions, malformed lengths, duplicate fields, invalid UTF-8, oversized payloads, and unsupported operations. It must never parse a generic binary response as a profile without the MCPS magic and a matching outstanding request.

## Privacy and abuse safeguards

- Sharing is opt-in on each device and field-by-field.
- Never transmit private notes, local contact labels, favourites, or local-only metadata.
- Do not automatically open received Discord links.
- Do not accept blank values as instructions to erase local profile data.
- Do not overwrite local profile fields automatically.
- Limit one outstanding request per peer, apply a cooldown, cap retries, and bound the receive payload before parsing.
- Keep received profile claims separate from the sender's signed MeshCore advert identity. A verified advert authenticates the advert, not arbitrary profile fields.

## Implementation order

1. Confirm the firmware-side binary request handler and response identity semantics.
2. Add a pure Kotlin MCPS codec and common tests.
3. Add a firmware handler in the custom firmware fork, with matching codec tests.
4. Add app-side request correlation, deduplication, opt-in settings, and safe persistence.
5. Test with two custom nodes, then verify that a stock MeshCore client sees no chat message and that unsupported peers remain unaffected.

## Current decision

Do not ship an Android-only implementation yet. The app has the outgoing binary-request builder and response event plumbing, but the peer-side MCPS request handler has not been confirmed in this repository. Implementing only the sender would produce silent failures or malformed behaviour rather than a working profile sync.
