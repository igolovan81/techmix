# GitHub Actions CI Pipeline

This project uses GitHub Actions for Continuous Integration (CI).

## Workflow Location
The workflow file is located at `.github/workflows/ci.yml`.

## What It Does
- Runs on every push and pull request to the `main` branch
- Checks out the code
- Sets up JDK 17
- Builds the project using Maven
- Runs all tests


## How to Use
1. **Push or create a pull request** to the `main` branch.
2. The workflow will automatically start. You can see the status of the workflow in the **Actions** tab of your GitHub repository.
3. If the build or tests fail, you will see details in the workflow logs.

## Customization
You can modify `.github/workflows/ci.yml` to:
- Change the Java version
- Add more build steps
- Deploy artifacts

For more information, see the [GitHub Actions documentation](https://docs.github.com/en/actions).
