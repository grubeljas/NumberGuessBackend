# NumberGuessBackend

## Quick Start

1. **Build the app:**
   ```bash
   ./gradlew clean build
   ```

2. **Launch Spring Boot:**
   ```bash
   ./gradlew bootRun
   ```

3. **Test WebSocket connection:**
   - Open [WebSocket King](https://websocketking.com/)
   - Connect to: `ws://localhost:8080/ws`
   - Multiple connections supported

4. **Send requests:**
   ```json
   {"nickname":"john", "betAmount":"1", "pickedNumber":"7"}
