package dev.wsc.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

/** Small shared set of View components; colors, shapes and typography come from resources. */
final class UiViews {
    private final Context context;
    UiViews(Context context) { this.context = context; }
    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    int dim(int resource) { return context.getResources().getDimensionPixelSize(resource); }
    int color(int attribute) { return MaterialColors.getColor(context, attribute, 0); }
    LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    LinearLayout row() {
        LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    void add(LinearLayout parent, View child, int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(gap); parent.addView(child, params);
    }
    void gap(LinearLayout parent, int value) { parent.addView(new View(context), new LinearLayout.LayoutParams(1, dp(value))); }
    TextView text(CharSequence value, int appearance) {
        TextView view = new TextView(context); view.setTextAppearance(appearance); view.setText(value);
        view.setTextColor(color(com.google.android.material.R.attr.colorOnSurface)); return view;
    }
    TextView body(CharSequence value) {
        TextView view = text(value, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        view.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant)); return view;
    }
    TextView title(CharSequence value) { return text(value, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium); }
    void heading(LinearLayout parent, int title, CharSequence subtitle) {
        TextView heading = text(context.getText(title), com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        ViewCompat.setAccessibilityHeading(heading, true); add(parent, heading, 6);
        if (subtitle != null) add(parent, body(subtitle), 20);
    }
    NestedScrollView page(LinearLayout content) {
        NestedScrollView scroll = new NestedScrollView(context); scroll.setFillViewport(true);
        FrameLayout wrapper = new FrameLayout(context) {
            @Override protected void onMeasure(int width, int height) {
                int max = getResources().getDimensionPixelSize(R.dimen.content_max_width);
                int inset = Math.max(0, (View.MeasureSpec.getSize(width) - max) / 2);
                if (getPaddingLeft() != inset) setPadding(inset, 0, inset, 0);
                super.onMeasure(width, height);
            }
        };
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        content.setPadding(dim(R.dimen.page_padding), dim(R.dimen.item_gap), dim(R.dimen.page_padding), dp(24)); wrapper.addView(content, params);
        scroll.addView(wrapper, new ViewGroup.LayoutParams(-1, -2)); return scroll;
    }
    MaterialCardView card(LinearLayout content) {
        MaterialCardView card = new MaterialCardView(context);
        int pad = dim(R.dimen.card_padding); content.setPadding(pad, pad, pad, pad); card.addView(content); return card;
    }
    void menuIcon(android.view.MenuItem item, int icon) {
        item.setIcon(icon);
        androidx.core.view.MenuItemCompat.setIconTintList(item, android.content.res.ColorStateList.valueOf(
                color(com.google.android.material.R.attr.colorOnSurfaceVariant)));
    }
    MaterialButton button(int label, int icon, Runnable action) {
        MaterialButton button = new MaterialButton(context); button.setText(label);
        if (icon != 0) button.setIconResource(icon);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setOnClickListener(v -> action.run()); return button;
    }
    Chip chip(CharSequence label, boolean warning) {
        Chip chip = new Chip(context, null, com.google.android.material.R.attr.chipStyle);
        chip.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        chip.setText(label); chip.setEnsureMinTouchTargetSize(false); chip.setChipMinHeight(dp(32));
        chip.setChipStrokeWidth(0); chip.setCheckable(false); chip.setClickable(false); chip.setFocusable(false);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(color(warning
                ? com.google.android.material.R.attr.colorErrorContainer : com.google.android.material.R.attr.colorPrimaryContainer)));
        chip.setTextColor(color(warning ? com.google.android.material.R.attr.colorOnErrorContainer : com.google.android.material.R.attr.colorOnPrimaryContainer));
        return chip;
    }
    ImageView icon(int resource, int size) {
        ImageView view = new ImageView(context); view.setImageResource(resource);
        view.setImageTintList(android.content.res.ColorStateList.valueOf(color(com.google.android.material.R.attr.colorPrimary)));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size))); return view;
    }
    void section(LinearLayout page, int label) {
        gap(page, 16);
        TextView view = text(context.getText(label), com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        view.setTextColor(color(com.google.android.material.R.attr.colorPrimary));
        ViewCompat.setAccessibilityHeading(view, true); add(page, view, 8);
    }
    View divider() {
        View divider = new View(context); divider.setBackgroundColor(color(com.google.android.material.R.attr.colorOutlineVariant));
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1))); return divider;
    }
    View setting(int icon, int label, CharSequence summary, View trailing, Runnable action) {
        LinearLayout row = row(); row.setMinimumHeight(dp(76)); int pad = dim(R.dimen.item_gap); row.setPadding(pad, pad, pad, pad);
        row.addView(icon(icon, 24));
        LinearLayout words = column(); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(dp(16)); params.setMarginEnd(dp(12)); row.addView(words, params);
        add(words, title(context.getText(label)), 4); if (summary != null) add(words, body(summary), 0);
        if (trailing == null) trailing = icon(R.drawable.ic_chevron_right, 24);
        row.addView(trailing);
        if (action != null) {
            android.util.TypedValue value = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            row.setBackgroundResource(value.resourceId); row.setFocusable(true); row.setOnClickListener(v -> action.run());
        }
        return row;
    }
    void metadata(LinearLayout parent, int label, CharSequence value) {
        TextView caption = text(context.getText(label), com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        caption.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant)); add(parent, caption, 4);
        TextView content = text(value, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        content.setTextIsSelectable(true); add(parent, content, 16);
    }
}
