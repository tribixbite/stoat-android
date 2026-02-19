# Server Load & Efficiency Comparison

*Comparison between the **Official Client** (legacy) and an **Optimized Client** (addressed issues).*

## Scenario Parameters
- **Server Size:** 300 active members.
- **Activity Level:** 1 presence update/sec (status/online state toggles across the server).
- **Messaging:** 10 messages/minute.
- **Session Duration:** 1 hour of active use with the member list open.

---

## 1. Data Usage Analysis

### Official Client
The primary driver of load is the **Full Member List Re-fetch** on every user cache change.

| Activity | Frequency | Calculation | Total Data |
| :--- | :--- | :--- | :--- |
| **Login** | 1/session | 3 serial calls + headers | ~50 KB |
| **Member List** | 3,600/hr | 3,600 calls * (300 members * ~1 KB/member) | **~1.08 GB** |
| **Msg Metadata** | 600/hr | redundant `fetchUser`/`fetchMember` calls | ~1.2 MB |
| **Total** | | | **~1.08 GB / hr** |

### Optimized Client
Addresses re-fetching and redundant calls.

| Activity | Frequency | Calculation | Total Data |
| :--- | :--- | :--- | :--- |
| **Login** | 1/session | 3 parallel calls | ~40 KB |
| **Member List** | 1/session | 1 call * (300 members * ~1 KB/member) | ~300 KB |
| **Status Updates** | 3,600/hr | Incremental WebSocket frames (~200B each) | ~720 KB |
| **Msg Metadata** | 600/hr | Cache checks prevent all redundant calls | ~0 KB |
| **Total** | | | **~1.06 MB / hr** |

**Efficiency Gain:** **~1,000x reduction in data usage.**

---

## 2. Server Request Volume (Load)

| Client Version | Requests per Hour | Requests per Day (8h use) |
| :--- | :--- | :--- |
| **Official** | ~3,700 | ~29,600 |
| **Optimized** | ~5 | ~40 |

**Impact:** A single "Official" user generates as much API load as **740** "Optimized" users. 

---

## 3. Performance & Battery Impact

### Official Client
- **Radio Activity:** The mobile radio stays in a high-power state continuously to handle the 1 request/sec duty cycle.
- **CPU/Jank:** Every second, the app parses ~300KB of JSON, clears a 300-item list, and performs a full UI recomposition of the member list.
- **Latency:** Sequential startup calls result in a **~2-4s** "blank screen" experience on login.

### Optimized Client
- **Radio Activity:** Uses low-power "long poll" or active WebSocket state; radio can enter low-power modes between message bursts.
- **CPU/Jank:** UI updates are granular (single list item change).
- **Latency:** Parallelized startup reduces initial load to **<0.5s**.

---

## Conclusion
The official client's behavior in busy servers is indistinguishable from a **self-inflicted Denial of Service (DoS)** against the backend. Moving from the official client to an optimized one reduces server operational costs by three orders of magnitude and significantly improves mobile battery life.
