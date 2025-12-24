# Error Handling Reference
| Scenario | Trigger | HTTP Status | Response Body |
|----------|---------|-------------|---------------|
| Unknown profile | `graphHopper.getProfile(name)` returns `null` | 400 | `{ "message": "Unknown profile: <name>" }` |
| CH missing & fallback disabled | No CH graph for profile and `enableFallback=false` | 400 | `{ "message": "CH not available for profile and fallback disabled" }` |
| Matrix too large | `sources.size() * targets.size()` exceeds 25,000,000 (5000×5000) | 400 | `{ "message": "Matrix too large" }` |
| Snap failure | `LocationIndex.findClosest(...)` returns invalid `Snap` | 200 | `failures` array contains the point index; affected rows/columns pre-filled with `-1` |
| Unreachable route | `Path.isFound()` is false or `ConnectionNotFoundException` thrown | 200 | Individual cell set to `-1` in both `distances` and `times` |
| Matrix interrupted | Worker thread interrupted while awaiting futures | 500 | `{ "message": "Matrix computation interrupted" }` |
| Worker exception | Any runtime exception bubbling out of a row task | 500 | `{ "message": "Matrix computation failed: <cause>" }` |

## JSON Shapes
Successful responses always follow the `MatrixResponse` schema:
```json
{
  "distances": [[long, ...], ...],
  "times": [[long, ...], ...],
  "failures": [int, ...]
}
```
Error responses are unified through `MatrixResource#errorResponse` and always include a `message` field:
```json
{
  "message": "Human-readable explanation"
}
```

## Snap Failure Workflow
1. All points are snapped once before any routing occurs.
2. Invalid snaps are collected into `failures` and stored in the response.
3. The corresponding source rows and target columns are pre-filled with `-1` so downstream systems can skip them without additional lookups.

## Unreachable Paths
- Both CH and fallback algorithms wrap `calcPath` calls.
- When the path is not found or a `ConnectionNotFoundException` is raised, the helper stores `-1` distance/time.
- This keeps matrix dimensions intact while signaling that the graph contains no viable route for that pair.
