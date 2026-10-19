# Security and privacy

Newz promises that it never contacts Google and never sends your data anywhere except to the
publishers whose feeds you follow. A bug that breaks that promise is a security issue.

## Reporting a problem

Please report privately through GitHub's
[private vulnerability reporting](https://github.com/mack5imal/newz/security/advisories/new)
rather than in a public issue. Useful details:

- what happens and how to reproduce it;
- the Newz version and your Android version;
- for a network leak, the host that was contacted and the feed or site that caused it.

This is a one-person project, so replies may take a few days. Confirmed issues are fixed in the
next release and credited in the advisory unless you would rather stay anonymous.

## In scope

- Any request to a Google-owned host, or a way around `HostGuard`.
- Data leaving the device other than feed and picture requests to publishers.
- Malicious feed or page content that can run code, crash the app repeatedly, or read app data.
- Problems with backup files (import of a crafted file, or private data written where other
  apps can read it).
