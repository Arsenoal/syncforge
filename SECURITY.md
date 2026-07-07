# Security policy

## Supported versions

| Version | Supported |
|---------|-----------|
| `1.0.x` | Supported — security fixes in patch releases |
| `0.9.0-rc.x` | Unsupported — upgrade to `1.0.0` |
| `< 0.9.0` | Not supported |

## Reporting a vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

Report security issues privately:

1. Open a [GitHub Security Advisory](https://github.com/Arsenoal/syncforge/security/advisories/new)
   (preferred), or
2. Email the maintainer via the contact on their GitHub profile with:
   - Description of the issue
   - Steps to reproduce
   - Impact assessment (if known)
   - SyncForge version and platform (Android, iOS, JVM)

We aim to acknowledge reports within **72 hours** and will coordinate disclosure
and a fix before public details when appropriate.

## Scope

SyncForge handles sync transport, local persistence, and optional auth token
storage on client devices. Backend security for your API remains your
responsibility — see [docs/REST_API.md](docs/REST_API.md).