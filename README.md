# Roadmap 🚗

1. Make mockup for all the possible features (main feature - generate Excel reports, later - generate wages to pay employees for hourly work)
  - Mockup includes clickable buttons and pages but no functionality.
  - Preferably done by `23.09.2026`
2. Functionalities:
  - generate monthly reports based on worksheets at the end of the work day
    TODO: Use PP-Structure intelligent document analysis system for PDF document and image scanning: [link to documentation](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/cross_platform/android_deployment.html)
  - Preferably done by `06.10.2026`
    TODO: Make a "learning mode" for when it is only started to be used, always ask for what is seen as the character, otherwise use 80% ocr if it is convinced the letter or number is correct.
  - Preferably done by `20.10.2026`
3. Extras:
  - Create a view for calculated salaries based on the worksheet data.
    TODO: calculation logic
    TODO: automatic payments with safe credential storage (using Swedbank real life transactions between 2 accounts)

# What is used in the project
- Kotlin with Jetpack Compose
- Dependencies: PaddleOCR (Optical Character Recognition), specifically PP-Structure for reading table data
