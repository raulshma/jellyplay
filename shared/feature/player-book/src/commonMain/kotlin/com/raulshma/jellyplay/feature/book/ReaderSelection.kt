package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Copy
import com.composables.icons.tabler.outline.Note
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Underline
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_annotations
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_annotations_empty
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_dialog_cancel
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_dialog_save
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_export_json
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_export_markdown
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_note
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_note_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Selection → mark UI: the bottom-anchored action row (color dots, underline
 * toggle, copy, note), the note dialog, and the annotations sheet (list +
 * export). All state is caller-owned; these composables only render and
 * dispatch, so the same row serves create (fresh selection) and edit
 * (selection sitting on an existing annotation).
 */

/**
 * The Compose swatch of a palette slot — mirrors reader.js's
 * `ANNOTATION_COLORS` exactly so the native chips preview the exact tint the
 * WebView will paint.
 */
internal fun ReaderAnnotationColor.swatch(): Color = when (this) {
    ReaderAnnotationColor.YELLOW -> Color(0xFFFFEE58)
    ReaderAnnotationColor.GREEN -> Color(0xFF66BB6A)
    ReaderAnnotationColor.BLUE -> Color(0xFF42A5F5)
    ReaderAnnotationColor.RED -> Color(0xFFEF5350)
}

/** The four-swatch palette row shared by the selection bar and annotation rows. */
@Composable
private fun AnnotationColorDots(
    selected: ReaderAnnotationColor?,
    onSelect: (ReaderAnnotationColor) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ReaderAnnotationColor.entries.forEach { color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .size(if (isSelected) 26.dp else 20.dp)
                    .background(color.swatch(), CircleShape)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color) },
            )
        }
    }
}

/**
 * The selection action row, bottom-anchored above the bottom chrome. Two
 * modes over one row:
 * - CREATE (no [existing]): a color dot paints a highlight (or underline
 *   while [style] is UNDERLINE), Copy exports the text, the note button
 *   opens the dialog (which saves with this style/color snapshot).
 * - EDIT ([existing] sits on this CFI): the dots recolor the mark, the
 *   underline toggle restyles it, Delete removes it, the note pencil edits
 *   the note. Copy stays.
 *
 * [style]/[color] are the caller-owned "pending mark" state — the caller
 * needs the same snapshot when the note dialog saves, so the toggle cannot
 * live in here.
 */
@Composable
internal fun SelectionActionBar(
    existing: ReaderAnnotation?,
    style: ReaderAnnotationStyle,
    color: ReaderAnnotationColor,
    onColorTap: (ReaderAnnotationColor) -> Unit,
    onToggleStyle: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            AnnotationColorDots(
                selected = if (existing != null) existing.color else color,
                onSelect = onColorTap,
            )
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = onToggleStyle) {
                Icon(
                    imageVector = Tabler.Outline.Underline,
                    contentDescription = null,
                    tint = if (style == ReaderAnnotationStyle.UNDERLINE || existing?.style == ReaderAnnotationStyle.UNDERLINE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White
                    },
                )
            }
            IconButton(onClick = onCopy) {
                Icon(imageVector = Tabler.Outline.Copy, contentDescription = null, tint = Color.White)
            }
            IconButton(onClick = onEditNote) {
                Icon(
                    imageVector = if (existing?.note != null) Tabler.Outline.Pencil else Tabler.Outline.Note,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            if (existing != null) {
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Tabler.Outline.Trash, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

/**
 * The note dialog: a single text field over an annotation. Saving an empty
 * field clears the note (the repository's empty-string contract maps to a
 * NULL note); saving from a fresh selection creates the annotation with the
 * given style/color snapshot.
 */
@Composable
internal fun NoteDialog(
    initialNote: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialNote.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.book_reader_note)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(Res.string.book_reader_note_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.ifBlank { null }) }) {
                Text(stringResource(Res.string.book_reader_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.book_reader_dialog_cancel))
            }
        },
    )
}

/**
 * The annotations sheet: every mark of this book, newest first (the repo
 * stream is oldest-first). One row = style chip + anchor text (italic, two
 * lines) + note preview + chapter label, with the compact palette for
 * recoloring and the note/delete actions; tapping the row jumps to the CFI.
 * The footer exports the whole set as Markdown / JSON (to the clipboard —
 * the caller confirms via the toast).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnnotationsSheet(
    annotations: List<ReaderAnnotation>,
    onJump: (ReaderAnnotation) -> Unit,
    onEditNote: (ReaderAnnotation) -> Unit,
    onRecolor: (ReaderAnnotation, ReaderAnnotationColor) -> Unit,
    onDelete: (ReaderAnnotation) -> Unit,
    onExport: (asJson: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_annotations))
        if (annotations.isEmpty()) {
            SheetEmptyText(text = stringResource(Res.string.book_reader_annotations_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(annotations.asReversed(), key = { it.id }) { annotation ->
                    AnnotationRow(
                        annotation = annotation,
                        onJump = { onJump(annotation) },
                        onEditNote = { onEditNote(annotation) },
                        onRecolor = { color -> onRecolor(annotation, color) },
                        onDelete = { onDelete(annotation) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { onExport(false) }) {
                Icon(imageVector = Tabler.Outline.Copy, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(Res.string.book_reader_export_markdown))
            }
            TextButton(onClick = { onExport(true) }) {
                Text(stringResource(Res.string.book_reader_export_json))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** One annotation row: chip + text block on top, palette + actions below. */
@Composable
private fun AnnotationRow(
    annotation: ReaderAnnotation,
    onJump: () -> Unit,
    onEditNote: () -> Unit,
    onRecolor: (ReaderAnnotationColor) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onJump() }
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnnotationStyleChip(annotation)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = annotation.anchorText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!annotation.note.isNullOrBlank()) {
                    val note = annotation.note.orEmpty()
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (annotation.chapterLabel.isNotBlank()) {
                    Text(
                        text = annotation.chapterLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onEditNote) {
                Icon(
                    imageVector = Tabler.Outline.Pencil,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Tabler.Outline.Trash,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            AnnotationColorDots(selected = annotation.color, onSelect = onRecolor)
        }
    }
}

/** The mark's visual identity: a filled swatch (highlight) or a thin bar (underline). */
@Composable
private fun AnnotationStyleChip(annotation: ReaderAnnotation) {
    when (annotation.style) {
        ReaderAnnotationStyle.HIGHLIGHT -> Box(
            modifier = Modifier
                .size(width = 18.dp, height = 24.dp)
                .background(
                    annotation.color.swatch().copy(alpha = 0.4f),
                    RoundedCornerShape(3.dp),
                ),
        )
        ReaderAnnotationStyle.UNDERLINE -> Box(
            modifier = Modifier.size(width = 18.dp, height = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(annotation.color.swatch()),
            )
        }
    }
}

/** The shared "this sheet has nothing to show" line. */
@Composable
private fun SheetEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
    )
}
