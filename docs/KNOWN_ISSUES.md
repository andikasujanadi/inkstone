# Known issues

## Vector color selection and fill — reported 24 October 2026

On the user’s device, changing the Vector color can update the displayed color code without updating the effective color, and dragging the color onto a closed shape does not reliably recolor it. The user asked to record this and defer the fix. It remains unresolved in 0.1.8; local geometry/fill tests do not establish that this device interaction works.
