# Music sources: what we run, what we dropped, what is worth adding

Supersedes the 2026-07-29 analysis. That document asked one question — which *new* sources
to add — and answered it with live HTTP probes. This one keeps those findings, corrects the
premise underneath them, and adds the two questions that turned out to matter more: whether
Icons8 belonged here at all, and how many results we should be asking each service for.

Claims about our own code are cited by `file:line`. Claims inherited from the July probes
are marked as such and were **not** re-verified — see *Verification still owed* at the end.

---

## 1. Icons8 is gone

It was never a Creative Commons source. Its music arm is **Fugue by Icons8**, a royalty-free
stock marketplace: API assets require an active subscription, and the free tier is
non-commercial under Icons8's own terms. It belonged in the "not Creative Commons" list
below, not in the search.

The code had already conceded this. `Icons8.kt` hard-coded `license = CCLicense.UNKNOWN` —
the only service that never derived a licence from its response. Everything downstream
followed from that one line:

- Every Icons8 row rendered "Licence unknown".
- `allowsCommercialUse()` is `false` for `UNKNOWN`, so **every Icons8 row was hidden the
  moment anyone ticked "Commercial use OK"** — the single most useful filter on the page.
- The rows appeared under no licence chip but that one.

Two smaller faults, both now moot: `externalLink` pointed at a raw preview audio file rather
than a track page (every other service links to a landing page), and `popularityMetric()`
promised "plays" while the parser never set `popularity`, so every row read "no popularity
signal".

**Accepted cost.** Icons8 was one of only two services reporting BPM, so the tempo filter and
`SearchPage.tempoRange` are now fed by ccMixter alone. Nothing in the research below restores
it — Audius carries a `bpm` field but fails on licence coverage. This is permanent, and it is
still the right trade: a licence the site cannot name is worse than a tempo it cannot show.

---

## 2. Result limits raised: 195 → 525

Each service hard-codes its cap in its request URL. What changed:

| Service | Was | Now | Why |
|---|---|---|---|
| Internet Archive | `rows=50` | **150** | The safest raise of the lot. `rows` is spent *before* the `mediatype == "audio"` filter and `toSearchResult`'s `mapNotNull` discard unusable items, so the archive was already delivering fewer results than we asked for. This closes a gap rather than widening the page. |
| Freesound | `page_size=50` | **150** | Documented ceiling. Already weighted 0.5 in the ranker. |
| Jamendo | `limit=50` | **200** | Documented ceiling. |
| ccMixter | `limit=25` | **25** | Hard ceiling, not a choice: above 25 it answers `200` with an empty body (`CCMixter.kt:28`). |

**The UI needed no work.** `search.js` already has `PAGE_SIZE = 25` and a `#load-more`
button, and the server already shipped the entire ranked set as DOM rows. More results simply
give that pager more to page through. `apply()` is O(all rows) per filter click, but a few
hundred iterations is sub-millisecond — measured against the row count, not assumed.

### Two things had to be fixed first

**A missing request timeout.** `ApiClientViaHttp` bounded only *connection* setup. Since
`SearchEngine` awaits every service before it can render, a server that accepted the
connection and then went quiet held the whole page open indefinitely. Larger pages are slower,
which would have turned a latent bug into a routine one. There is now an 8-second request
timeout, and the single `HttpClient` is reused so its connection pool survives between
searches instead of being thrown away on every call.

**A per-row inline SVG.** The "Listen" icon was a 489-byte `<svg>` repeated once per row —
about 16% of a ~3.1 KB row. It is now defined once as a `<symbol>` and referenced with
`<use>`. At the old ceiling that saved ~95 KB; at the new one, ~250 KB of duplicated markup.

### What will visibly change

`RelevanceRanker` fuses on rank with `K = 60`, so rank 1 always scores `1/61` no matter how
deep the fetch — **the top of the ranking cannot move because of a bigger page**. But
`popularityRanks` computes popularity rank *within the fetched window*, so a deeper window
does reshuffle which results reach the top. That is the ranker working as designed on better
data, not a regression, but it is the thing to look at first after deploying.

ccMixter's fixed 25 means it can never contribute past rank 25, so its share of the tail
shrinks as the others deepen. A conscious trade.

---

## 3. Free Music Archive: no, twice over

Two separate blockers, either of which is sufficient:

1. **The API is dead.** The July probe got `404` from `/api/get/tracks.json` while the site
   itself answered `200`. FMA shut its public API down under server load.
2. **Their terms would forbid us anyway.** FMA's app-developers page prohibits hotlinking,
   and prohibits forwarding user search queries to their search engine or scraping the
   returned HTML without explicit approval. That is precisely what this app does. A restored
   API would not make FMA usable without written permission.

**On "only audio samples?"** — that describes **Freesound**, not FMA. FMA is ~13,000 curated
full tracks by independent artists; Freesound is the sample library, and it is the one we
actually integrate. It stays, contained by the `duration:[60 TO *]` filter and its 0.5 weight
in `RelevanceRanker.serviceWeights`. Sharpening that further (excluding loops and one-shots
via Freesound's own filter terms) is open, and needs API access to get the field names right.

---

## 4. New sources: fresh pass

### Added: Library of Congress

`https://www.loc.gov/audio/?q=…&fo=json` needs **no key at all** — the Library rate-limits
rather than authenticating. The National Jukebox is 5,882 recordings from 1900–1925, public
domain under the Music Modernization Act: a genuinely distinct catalogue nothing else here
touches, and the only one that is unambiguously *music* rather than sound.

**The licence is decided per record, from its date.** loc.gov states rights as free prose
rather than as a licence URL, so `CCLicense.fromUrl` has nothing to read — and the prose it
does state is unhelpful: every Jukebox record carries `item.rights_advisory` = *"Inclusion of
the recording in the National Jukebox, courtesy of Sony Music Entertainment or EMI Music"*,
wording that predates the Music Modernization Act.

So the date decides instead. Under the MMA a US recording enters the public domain on 1
January 101 years after publication, which today releases everything up to and including
1925. A record at or before that line is `PUBLIC_DOMAIN`; anything after it is `UNKNOWN`,
because `PUBLIC_DOMAIN` sets `allowsCommercialUse()` and would tell someone they may sell a
recording that is still somebody's — the mistake Icons8 was removed for. The cut-off is
computed rather than written down, so it advances on its own each January; the `Clock` is
injected so tests can pin it.

No weight penalty in `RelevanceRanker`: unlike Europeana and Freesound this is a music
collection, so it competes on equal footing.

### Evaluated and rejected: Europeana

Built, tested against the live API, and dropped. `TYPE:SOUND` is not `TYPE:MUSIC`, and that
turned out to matter exactly as much as feared.

`https://api.europeana.eu/record/v2/search.json` with `qf=TYPE:SOUND&reusability=open`: free
self-serve key, `rows` maxes at 100, `reusability=open` restricts results to Public Domain
Mark, CC0, CC BY and CC BY-SA, and the licence arrives as a URL that `CCLicense.fromUrl` reads
without new mapping. All of that works. The catalogue is the problem.

**The relevance check.** `q=jazz` looked encouraging — Romanian Radio chamber and jazz
recordings, all genuinely music. `q=guitar` is the query that tells the truth, and it returned
**7 music in the top 20**:

| | count |
|---|---|
| Music | 7 |
| "Sounds of Changes" — foley of guitar *making*: wood chisel, planing, sandpaper, honing the planer | 11 |
| Romanian Radio poetry readings — "Ghitara" and "Chitara" are poem titles | 2 |

More than half of one page is a single provider's recordings of a luthier's workshop. This is
the failure that ruled out Openverse's Wikimedia catalogue, repeated: the query matched the
*word* guitar, not the instrument being played. A service weight does not help, because the
noise arrives inside the same 100 rows as the signal.

**And the catalogue is thin.** `totalResults` was **141** for a mainstream instrument across
the whole of Europeana's openly-licensed sound. Even a perfect filter leaves roughly fifty
usable recordings for "guitar", against a service integration, an API key and its maintenance.

**Why not just filter it.** Every non-music result had an empty `dcTypeLangAware.en`, while
six of the seven music results carried one — so filtering on that field would work. But what
it actually selects is *providers who fill in a type field*: a proxy for cataloguing
diligence, not for music, which breaks the day a thorough non-music provider appears. The one
genuinely music-semantic signal, Europeana's own `soundgenres/Music/…` concept branch,
appeared on 2 of 20 — high precision, almost no recall.

#### What the integration taught us anyway

Worth keeping, because it applies to the next aggregator as much as to this one. A real
response falsified **three of the four field names** taken from the documentation:

| Documented | Actually sent | Consequence had it shipped |
|---|---|---|
| `edmRights` | `rights` | none — the parser already fell back |
| `dcCreator` | absent | byline falls to `dataProvider`, the broadcaster |
| `year` | absent | **every row dated by ingest, not recording** |
| `dcSubject` | `dcSubjectLangAware` | **no tags at all, on any row** |

The date was the costly one. With no `year`, a parser falls through to `timestamp_created` —
when Europeana *ingested* the record. A concert given on `2011-03-08` was catalogued on
`2023-04-12`, so every row would have carried the wrong decade while looking entirely
plausible. The recording date lives in `edmTimespan`, whose language-aware label is tagged
`zxx`: the ISO code for "no linguistic content", because a date is not a word.

Subjects arrive as `dcSubjectLangAware`, an object keyed by language, and the `def` key holds
Getty and Europeana vocabulary URIs rather than words.

**Do not paste a raw Europeana response anywhere.** It echoes the caller's `wskey` back inside
`guid`, as `utm_campaign`, and inside `link`.

### Rejected on access, not content: Dogmazic

61,618 tracks, 4,731 artists, 525 labels, and **every track under a free licence** — the best
catalogue fit found anywhere, and the only candidate that is all-free by construction the way
ccMixter is. Blocked on one thing: authentication.

Dogmazic runs **Ampache**, a self-hosted music server. Its API is not an open catalogue
endpoint like Jamendo's; it is the remote-control interface a media player uses to reach
*your* library, and it assumes an account. Access works one of two ways, and neither is
anonymous:

- **Handshake** — you send `action=handshake` with a timestamp and
  `SHA256(timestamp + SHA256(password))`, and get back a session token that expires. This
  needs a real username and password on their server.
- **API key** — a longer-lived token you pass instead. Only a server *administrator* can
  generate one, and only for a specific user account.

Ampache also speaks **Subsonic**, an older and much more widely implemented API for the same
job — self-hosted music servers exposing a personal library to players like DSub or
play:Sub. Subsonic is simpler but no help here: it authenticates on every single request
(`u=` plus a salted token, or in older versions the password itself), so it has the same
account requirement with worse ergonomics.

So integrating Dogmazic means holding credentials on someone else's server and searching as
a logged-in user — an arrangement they would have to agree to, not something we can simply
consume. **This is a relationship problem, not a technical one:** the code would be
straightforward once a key exists. Worth an email.

Two things to settle in that conversation: whether they are willing to issue an API key for a
search engine that will query them on every user search, and how their 44 licences map — the
Free Art License is a free licence but not a Creative Commons one, so it would land as
`UNKNOWN` and be hidden by the "Commercial use OK" filter exactly as Icons8's results were.

### Rejected

| Service | Why |
|---|---|
| **Openverse** | Its only catalogue not already integrated directly is `wikimedia_audio` — and that is not music. `q=guitar&source=wikimedia_audio` returned 20/20 non-music (tuning references, effect demos, Lingua Libre pronunciation clips), and `category=music` returns 0 because Wikimedia items carry `category: null`. Anonymous limits of 20/min and 200/day rule it out regardless. |
| **Smithsonian Open Access** | 4.5M CC0 records via `api.si.edu` with a free key, but the music — Folkways — is sold and licensed for a fee, outside the CC0 set. |
| **DPLA** | Needs a key; rights arrive as free-text plus RightsStatements.org URIs rather than CC URLs, and it is a library-metadata aggregator, not a music catalogue. |
| **Audius** | Tracks carry `release_date`, `duration`, `genre`, `tags` and `play_count`, and Audius *does* offer six CC licences to uploaders — but search still has no licence filter, and the July sample found 3 CC tracks in 248 across five queries. Capability is fine; coverage is not. Recheck if a licence filter ever ships. |
| **Musopen** | Cloudflare interstitial on `/api/v1/music/`, `403` to a server-side client. Not usable from Cloud Run. |
| **Funkwhale** (open.audio) | Instance alive, but `/api/v1/tracks/` and `/api/v1/artists/` both `404` anonymously. Small catalogue regardless. |
| **Ektoplazm** | All-CC and alive, but its WP REST API returns `401` and the newest post is Dec 2018 — dormant. |
| **Mirlo** | Open API, but a paid-download platform; CC licensing is optional per release. |
| **Incompetech** | Genuinely CC BY, but no API — HTML scraping only. |

**The pattern worth naming:** heritage and CC0 aggregators fail identically. Openverse (via
Wikimedia), Smithsonian and DPLA are enormous, properly-licensed catalogues with almost no
music in them. Breadth of *open licensing* is not breadth of *music*.

Europeana was the case for testing the pattern rather than assuming it: unlike the others it
can be asked for sound specifically. It was built, run against the live API, and rejected on
the results — `TYPE:SOUND` still selects for the *word*, and a search for a guitar returns a
luthier's workshop. Filtering to audio is not the same as filtering to music, and no
aggregator evaluated here has a filter for the difference.

The rule that follows: a candidate has to be a music catalogue, not a media catalogue with
music in it. The Library of Congress passed on exactly that basis, and Dogmazic would too.

### Not Creative Commons — don't be tempted

These dominate "free music API" search results but are royalty-free under *proprietary*
licences: **Icons8/Fugue** (which is how we got here), **Pixabay** (left CC0 in 2019),
**filmmusic.io**, **Mixkit**, **Uppbeat**, **Bensound**, **Loudly**.

---

## 5. What a new source costs

Updated against current `master` — the July list was written before the redesign and the
ranking change:

1. New class in `apiservices/`, modelled on `InternetArchive.kt` (the defensive one).
2. `SearchService` enum entry **and** `toService()` branch — `SearchResult.kt`.
3. `label()` and `popularityMetric()` in `SearchPage.kt` are exhaustive `when`s; the build
   fails until both get a branch.
4. A `.dot-<key>` colour in `search.css` — **not** a logo PNG. The redesign switched to CSS
   dots, and the files under `static/logos/` are referenced nowhere in the repo.
5. Fixture JSON in `apiresponses/` and a test class mirroring `FreesoundTest`'s four cases.
6. `.env.dist` entry if it needs a key, plus `README.md`, `about.mustache`, the source list in
   `metaDescription` (`SearchPage.kt`), and the JSON-LD description in `search.mustache`.
7. Optionally a `RelevanceRanker.serviceWeights` entry if the catalogue is not purely music.

**Two constraints from the July list no longer apply.** It stated that `SearchResult.date` is
non-null and `SearchEngine` sorts on it, and rejected candidates on that basis. Commit
`b05d7de` replaced date sorting with `RelevanceRanker` on **2026-07-29 — the same day those
probes were run**. Date now drives only the displayed label and the client-side "Newest"
button, so a source without a precise release date is no longer disqualified. Likewise
`popularity` is nullable and a service without a counter reuses its relevance order for both
rankings, so lacking one costs nothing.

---

## Verification still owed

Everything in §4 marked as inherited comes from the 2026-07-29 probes and was not re-run.
Europeana was checked against a live key and rejected on the results; see §4. Still to
confirm:

- That Jamendo accepts `limit=200` and Freesound honours `page_size=150`. Both are documented
  ceilings rather than observed ones. A rejected value costs that service's results and
  nothing else, since each catches its own failures, but it would be silent.
- Page weight at the new ceiling: `curl -s 'localhost:8080/?q=jazz' | wc -c`, plus the gzipped
  transfer size.

### Library of Congress: checked against a real response

The query works and the field shapes are now known, because a real `q=jazz` response was
captured and is the test fixture. Three things it corrected:

- **`contributor` is the wrong field for the artist.** It lists everyone involved in
  cataloguing order, which puts the composer or the conductor first as often as the
  performer — "Jazz baby" would have been credited to Rosario Bourdon, its conductor, rather
  than to Marion Harris, who sang it. `contributor_primary` is the field that names the act.
  Four of five sampled records would have shown the wrong artist.
- **Subject headings contain their own commas.** "ragtime, jazz, and more" is one Library
  heading, and the tag chips are built by splitting on commas — so it would have arrived as
  three chips, the last of them "and more".
- `date` is a full ISO date (`1923-11-07`), not the bare year that was assumed, and `dates`
  holds plain dates rather than timestamps.

It also surfaced the rights question that shaped the licence rule above: every sampled
record credits Sony Music Entertainment or EMI in `item.rights_advisory`, which is not a
public domain statement, while the MMA says those same 1918–1923 recordings are public
domain. Resolved by trusting the date rather than the catalogue note, and by declining to
claim anything for records past the line.

Still unverified for the Library:

- That loc.gov serves a plain `java.net.http` client over time: it is protective of automated
  traffic and `ApiClientViaHttp` sends no custom User-Agent. A 403 would be silent, showing up
  only as the Library never contributing results.
- Whether any record in the collection is dated after 1925. Those now show as "Licence
  unknown" rather than as public domain, which is safe but invisible under the "Commercial
  use OK" filter — worth knowing how many there are.
