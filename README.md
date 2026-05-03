# Backend Specification - AI Kid Storybook Publisher

## 1. System Overview
The backend is a Spring Boot application designed to orchestrate the generation of children's storybooks using AI. It manages story text generation, image generation, and PDF compilation, providing real-time status updates via Server-Sent Events (SSE).

## 2. Technology Stack
- **Runtime**: Java 21 (OpenJDK)
- **Framework**: Spring Boot 3.4.0
- **API**: Spring GraphQL 1.3.x (with Apollo compatibility)
- **Database**: PostgreSQL 17
- **ORM**: Spring Data JPA / Hibernate
- **PDF Generation**: Apache PDFBox 2.0.32
- **AI Orchestration**: RestTemplate / Pollinations AI API
- **Real-time Updates**: SSE (Server-Sent Events) via `SseEmitter`
- **Utilities**: Lombok, Jackson, Slf4j

## 3. Data Model
### StoryBook (Entity)
| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary Key (Identity) |
| `title` | `String` | Book title (Required) |
| `description` | `String` | Story prompt/description |
| `ageGroup` | `String` | Target age range (e.g., "5-8 year old") |
| `writingStyle` | `String` | Tone of the story (e.g., "magical") |
| `numberOfPages`| `Integer`| Total pages (Default: 5, Max: 10) |
| `status` | `String` | PENDING, DRAFTING, REVIEW_PENDING, GENERATING, COMPLETED, FAILED |
| `pdfStatus` | `String` | NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED |
| `lastStatus` | `String` | Human-readable latest status message |
| `pdfPath` | `String` | Path to generated PDF file |
| `fontColor` | `String` | CSS Color for story text |
| `fontSize` | `Integer` | Font size in pixels |
| `fontStyle` | `String` | Font family (Serif, Sans-Serif, etc.) |
| `textBackground`| `String` | Background opacity/color for text overlay |
| `createdAt` | `DateTime`| Auto-generated timestamp |

### StoryPage (Entity)
| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary Key |
| `pageNumber` | `Integer` | Index of the page |
| `text` | `String` | Story content for this page |
| `imageUrl` | `String` | URL of the AI-generated illustration |
| `storyBook` | `Relation`| Many-to-One with `StoryBook` |

## 4. API Specification
### GraphQL Interface
- **Queries**:
    - `getStoryBook(id: ID!)`: Fetch a specific book with pages.
    - `getAllStoryBooks`: Fetch all books (summary view).
- **Mutations**:
    - `generateStoryBook(...)`: Full automated generation.
    - `generateStoryDraft(...)`: Text-only generation for review.
    - `updateStoryContent(...)`: Update text and styles after drafting.
    - `finalizeAndGenerateImages(id: ID!)`: Start image and PDF generation after review.
    - `deleteStoryBook(id: ID!)`: Remove book and associated files.

### REST Endpoints
- `GET /api/status/{id}`: SSE stream for real-time progress.
- `GET /api/download/{id}`: Stream the PDF file to the client.

## 5. Core Logic & Services
### StoryGenerationService
- **Text Generation**: Uses `https://text.pollinations.ai/` (POST). Prompt includes `ageGroup`, `writingStyle`, and `numberOfPages`. Pages are separated by a custom delimiter (e.g., `---PAGE---`).
- **Image Generation**: Uses `https://image.pollinations.ai/prompt/{prompt}` (GET). Prompt includes `storyText` + keywords like "cartoon style", "colorful", "no text".
- **Workflow**: Launches `@Async` tasks to avoid blocking the GraphQL response.

### PdfGenerationService
- Uses **PDFBox** to create a landscape/portrait document.
- **Title Page**: Large title centered with cover image.
- **Story Pages**: High-res illustration as background with text overlay (based on user styles).
- **Compression**: Optimized for Amazon KDP upload standards.

### StatusEmitterService
- Manages a `Map<Long, SseEmitter>` to push updates to the frontend based on the book ID.

## 6. Infrastructure
- **Containerization**: `Dockerfile` (Multi-stage build using Maven and OpenJDK 21).
- **Persistence**: PostgreSQL container with volume mapping.
- **Storage**: Local directory for generated PDFs, mapped as a volume.
