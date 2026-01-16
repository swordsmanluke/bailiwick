# Bailiwick UI Design System

This document defines the visual design system for the Bailiwick Android app.

## Design Principles

| Aspect | Choice | Rationale |
|--------|--------|-----------|
| **Aesthetic** | Minimal & Clean | Focus on content, reduce visual noise |
| **Base System** | Material Design 3 | Native Android integration, modern patterns |
| **Theme** | Light only | Simpler implementation, consistent experience |
| **Colors** | Warm orange/amber | Energy, warmth, distinct from Facebook's blue |
| **Corners** | 12dp default | Balanced softness, modern feel |
| **Typography** | Roboto (system) | Native feel, no extra assets |
| **Spacing** | 4dp grid | Tight but consistent rhythm |

## Color Palette

### Primary Colors
The app uses a warm orange/amber theme reflecting warmth and community.

| Name | Value | Usage |
|------|-------|-------|
| Primary | `#ffc04a` | Main brand color, primary buttons, highlights |
| Primary Dark | `#ff9040` | Status bar, pressed states |
| Primary Light | `#ffe082` | Light backgrounds, hover states |

### Secondary Colors
| Name | Value | Usage |
|------|-------|-------|
| Secondary | `#ff6347` | Accent elements, notifications |
| Secondary Dark | `#c4412b` | Pressed states for secondary elements |
| Accent | `#3a3a3a` | Text on primary, icons |

### Background & Surface
| Name | Value | Usage |
|------|-------|-------|
| Background | `#fafafa` | Main app background |
| Surface | `#ffffff` | Cards, dialogs, input fields |
| Divider | `#e0e0e0` | Separators, borders |

### Text Colors
| Name | Value | Usage |
|------|-------|-------|
| Primary Text | `#212121` | Main body text, headings |
| Secondary Text | `#757575` | Captions, metadata |
| Hint Text | `#9e9e9e` | Placeholder text |
| On Primary | `#ffffff` | Text on primary-colored backgrounds |

### State Colors
| Name | Value | Usage |
|------|-------|-------|
| Error | `#b00020` | Error messages, validation |
| Success | `#4caf50` | Success confirmations |
| Warning | `#ff9800` | Warning messages |
| Info | `#2196f3` | Informational messages |

## Typography

### Scale
| Style | Size | Weight | Usage |
|-------|------|--------|-------|
| Headline 1 | 28sp | Bold | Screen titles |
| Headline 2 | 24sp | Bold | Section headers |
| Headline 3 | 20sp | Medium | Card titles |
| Body 1 | 16sp | Regular | Main content |
| Body 2 | 14sp | Regular | Secondary content |
| Caption | 12sp | Regular | Timestamps, labels |
| Button | 14sp | Medium | Button text |

### Font Family
- Primary: `sans-serif` (Roboto on Android)
- Medium weight: `sans-serif-medium`

## Spacing System

Based on a 4dp grid system:

| Token | Value | Usage |
|-------|-------|-------|
| `spacing_xxs` | 2dp | Micro adjustments |
| `spacing_xs` | 4dp | Tight spacing |
| `spacing_sm` | 8dp | Standard small spacing |
| `spacing_md` | 16dp | Default padding |
| `spacing_lg` | 24dp | Section spacing |
| `spacing_xl` | 32dp | Large gaps |
| `spacing_xxl` | 48dp | Major sections |

## Corner Radii

**Default: 12dp** (Medium) for cards, buttons, and containers.

| Token | Value | Usage |
|-------|-------|-------|
| `corner_radius_sm` | 8dp | Tags, small elements |
| `corner_radius_md` | 12dp | **Default** - cards, buttons, inputs |
| `corner_radius_lg` | 16dp | Emphasized containers |
| `corner_radius_xl` | 24dp | Pills, large rounded elements |
| `corner_radius_circle` | 50dp | Avatars, circular buttons |

## Elevation

| Token | Value | Usage |
|-------|-------|-------|
| `elevation_none` | 0dp | Flat elements |
| `elevation_low` | 2dp | Cards, surfaces |
| `elevation_medium` | 4dp | Raised buttons |
| `elevation_high` | 8dp | Dialogs, modals |

## Components

### Buttons

**Primary Button**
- Background: Primary color (`#ffc04a`)
- Text: Accent color (`#3a3a3a`)
- Corner radius: 12dp
- Min height: 48dp
- Horizontal padding: 16dp

**Secondary Button (Outlined)**
- Background: Transparent
- Border: Primary color, 1dp
- Text: Primary color
- Corner radius: 12dp

**Text Button**
- Background: Transparent
- Text: Primary color
- Min height: 32dp

### Cards

**Standard Card**
- Background: Surface color (`#ffffff`)
- Corner radius: 12dp
- Elevation: 2dp
- Padding: 16dp

**Post Card**
- Standard card + bottom margin 8dp

### Input Fields

**Text Input**
- Style: Outlined
- Corner radius: 12dp
- Border: Primary color when focused
- Padding: 12dp horizontal, 8dp vertical

### Chips

**Filter Chip**
- Height: 32dp
- Corner radius: 16dp (lg)
- Background: `#f5f5f5` (inactive), `#fff3e0` (active)

**Tag Chip**
- Height: 24dp
- Corner radius: 8dp (sm)
- Background: `#e3f2fd`
- Text: `#1565c0`

**Reaction Chip**
- Height: 28dp
- Corner radius: 16dp (lg)
- Background: `#f5f5f5`

### Avatars

| Size | Dimension | Usage |
|------|-----------|-------|
| XS | 24dp | Inline, compact lists |
| SM | 32dp | User chips |
| MD | 48dp | List items, post avatars |
| LG | 64dp | Profile headers |
| XL | 96dp | Large profile views |
| Profile | 120dp | Profile editing |

## Icons

- Size: 24dp (standard), 16dp (small), 32dp (large)
- Color: Use `app:tint` for theming
- Touch target: Minimum 48dp

## Animations

- Transition duration: 200ms (fast), 300ms (normal)
- Easing: Material Design standard curves

## Dark Mode (Future)

The design system includes dark mode color tokens:
- `colorBackgroundDark`: `#121212`
- `colorSurfaceDark`: `#1e1e1e`

These can be activated via `values-night/` resources.

## Accessibility

- Minimum touch target: 48dp
- Contrast ratio: 4.5:1 for normal text, 3:1 for large text
- Content descriptions on all interactive elements
- Support for dynamic text sizing

## Usage in Code

### XML Layouts
```xml
<!-- Use styles -->
<Button
    style="@style/Widget.Bailiwick.Button.Primary"
    android:text="@string/submit" />

<!-- Use dimensions -->
<View
    android:layout_margin="@dimen/spacing_md"
    android:elevation="@dimen/elevation_low" />

<!-- Use colors -->
<TextView
    android:textColor="@color/colorTextPrimary"
    android:background="@color/colorSurface" />
```

### Text Appearances
```xml
<TextView
    android:textAppearance="@style/TextAppearance.Bailiwick.Body1" />
```
