# Security Scanning with Snyk

This project supports security scanning using Snyk.

## Prerequisites
- Install the Snyk CLI: https://docs.snyk.io/snyk-cli/install-the-snyk-cli
- Authenticate with Snyk: `snyk auth`

## Scan the Project
To scan your Maven project for vulnerabilities, run:

```
snyk test
```

To monitor the project and send results to the Snyk dashboard:

```
snyk monitor
```

For more options, see the [Snyk CLI documentation](https://docs.snyk.io/snyk-cli/cli-reference).
