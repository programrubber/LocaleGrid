# Signals: What is and isn't worth logging

## Strong Signals (Log these)
1. **Direct User Correction**: "Please save reports in doc/, not the project root."
2. **Environment Peculiarity**: "On Windows, use gradlew.bat with specific JAVA_HOME; on Mac, use ./gradlew."
3. **Domain Constraint**: "LocaleGrid requires 2-space indentation when serializing JSON."
4. **Repeated Tool Failure**: A command that repeatedly fails due to a missing flag or path assumption.

## Weak Signals (Do NOT log)
1. One-off typos by the human user.
2. Temporary test data or transient network timeouts.
3. Information already well-documented and followed without deviation.
