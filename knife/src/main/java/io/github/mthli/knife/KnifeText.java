/*
 * Copyright (C) 2015 Matthew Lee
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.mthli.knife;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ParagraphStyle;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import io.github.mthli.knife.defaults.AligningDefault;
import io.github.mthli.knife.defaults.HeadingTagDefault;
import io.github.mthli.knife.glide.GlideApp;
import io.github.mthli.knife.glide.GlideImageGetter;
import io.github.mthli.knife.glide.GlideRequests;
import io.github.mthli.knife.spans.AlignmentSpan;
import io.github.mthli.knife.spans.ImageCustomSpan;
import io.github.mthli.knife.type.MediaImageType;
import io.github.mthli.knife.util.BitmapUtil;

public class KnifeText extends EditText implements TextWatcher {
    public static final int FORMAT_BOLD = 0x01;
    public static final int FORMAT_ITALIC = 0x02;
    public static final int FORMAT_UNDERLINED = 0x03;
    public static final int FORMAT_STRIKETHROUGH = 0x04;
    public static final int FORMAT_BULLET = 0x05;
    public static final int FORMAT_QUOTE = 0x06;
    public static final int FORMAT_LINK = 0x07;
    public static final int TEXT_COLOR = 0x08;
    public static final int HEADING_TAG = 0x09;
    public static final int TEXT_ALIGN = 0x10;

    private int bulletColor = 0;
    private int bulletRadius = 0;
    private int bulletGapWidth = 0;
    private boolean historyEnable = true;
    private int historySize = 100;
    private int linkColor = 0;
    private boolean linkUnderline = true;
    private int quoteColor = 0;
    private int quoteStripeWidth = 0;
    private int quoteGapWidth = 0;
    private boolean isLine = false;
    private boolean isLinePadding = false;
    private int lineColor = 0;

    private final List<Editable> historyList = new LinkedList<>();
    private boolean historyWorking = false;
    private int historyCursor = 0;

    private SpannableStringBuilder inputBefore;
    private Editable inputLast;
    private GlideRequests glideRequests;

    private Canvas canvas;
    private Rect mRect;
    private Paint mPaint;

    public KnifeText(Context context) {
        super(context);
        init(null);
    }

    public KnifeText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public KnifeText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @SuppressWarnings("NewApi")
    public KnifeText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        glideRequests = GlideApp.with(this);
        TypedArray array = getContext().obtainStyledAttributes(attrs, R.styleable.KnifeText);
        bulletColor = array.getColor(R.styleable.KnifeText_bulletColor, 0);
        bulletRadius = array.getDimensionPixelSize(R.styleable.KnifeText_bulletRadius, 0);
        bulletGapWidth = array.getDimensionPixelSize(R.styleable.KnifeText_bulletGapWidth, 0);
        historyEnable = array.getBoolean(R.styleable.KnifeText_historyEnable, true);
        historySize = array.getInt(R.styleable.KnifeText_historySize, 100);
        linkColor = array.getColor(R.styleable.KnifeText_linkColor, 0);
        linkUnderline = array.getBoolean(R.styleable.KnifeText_linkUnderline, true);
        quoteColor = array.getColor(R.styleable.KnifeText_quoteColor, 0);
        quoteStripeWidth = array.getDimensionPixelSize(R.styleable.KnifeText_quoteStripeWidth, 0);
        quoteGapWidth = array.getDimensionPixelSize(R.styleable.KnifeText_quoteCapWidth, 0);
        isLine = array.getBoolean(R.styleable.KnifeText_isLine, false);
        isLinePadding = array.getBoolean(R.styleable.KnifeText_isLinePadding, false);
        lineColor = array.getColor(R.styleable.KnifeText_lineColor, 0);
        array.recycle();

        setLine(isLine);

        if (historyEnable && historySize <= 0) {
            throw new IllegalArgumentException("historySize must > 0");
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        addTextChangedListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeTextChangedListener(this);
    }

    //https://stackoverflow.com/questions/21243969/drawing-background-lines-in-an-edittext-that-uses-a-custom-font-or-typeface
    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        this.canvas = canvas;
        if (isLine) {
            int height = getHeight();
            int line_height = getLineHeight();

            int count = height / line_height;

            if (getLineCount() > count)
                count = getLineCount();

            Rect r = mRect;
            Paint paint = mPaint;
            int baseline = getLineBounds(0, r);

            int left;
            int right;

            if (isLinePadding()) {
                left = r.left;
                right = r.right;
            } else {
                left = getLeft();
                right = getRight();
            }

            for (int i = 0; i < count; i++) {
                canvas.drawLine(left, baseline + 1, right, baseline + 1, paint);
                baseline += getLineHeight();
            }
        } else {
            clearLine();
        }

        super.onDraw(canvas);
    }

    public void setLine(boolean isLine) {
        this.isLine = isLine;
        if (isLine) {
            initLine();
        } else {
            clearLine();
        }
    }

    public boolean isLine() {
        return isLine;
    }

    public void setIsLinePadding(boolean status) {
        this.isLinePadding = status;
    }

    public boolean isLinePadding() {
        return isLinePadding;
    }

    private void initLine() {
        mRect = new Rect();
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setColor(getLineColor());
        clearLine();
    }

    private void clearLine() {
        if (canvas != null) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
    }

    public void lineColor(int color) {
        this.lineColor = color;
    }

    public int getLineColor() {
        return lineColor;
    }

    // StyleSpan ===================================================================================

    public void bold(boolean valid) {
        if (valid) {
            styleValid(Typeface.BOLD, getSelectionStart(), getSelectionEnd());
        } else {
            styleInvalid(Typeface.BOLD, getSelectionStart(), getSelectionEnd());
        }
    }

    public void italic(boolean valid) {
        if (valid) {
            styleValid(Typeface.ITALIC, getSelectionStart(), getSelectionEnd());
        } else {
            styleInvalid(Typeface.ITALIC, getSelectionStart(), getSelectionEnd());
        }
    }

    protected void styleValid(int style, int start, int end) {
        switch (style) {
            case Typeface.NORMAL:
            case Typeface.BOLD:
            case Typeface.ITALIC:
            case Typeface.BOLD_ITALIC:
                break;
            default:
                return;
        }

        if (start >= end) {
            return;
        }

        getEditableText().setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    protected void styleInvalid(int style, int start, int end) {
        switch (style) {
            case Typeface.NORMAL:
            case Typeface.BOLD:
            case Typeface.ITALIC:
            case Typeface.BOLD_ITALIC:
                break;
            default:
                return;
        }

        if (start >= end) {
            return;
        }

        StyleSpan[] spans = getEditableText().getSpans(start, end, StyleSpan.class);
        List<KnifePart> list = new ArrayList<>();

        for (StyleSpan span : spans) {
            if (span.getStyle() == style) {
                list.add(new KnifePart(getEditableText().getSpanStart(span), getEditableText().getSpanEnd(span)));
                getEditableText().removeSpan(span);
            }
        }

        for (KnifePart part : list) {
            if (part.isValid()) {
                if (part.getStart() < start) {
                    styleValid(style, part.getStart(), start);
                }

                if (part.getEnd() > end) {
                    styleValid(style, end, part.getEnd());
                }
            }
        }
    }

    protected boolean containStyle(int style, int start, int end) {
        switch (style) {
            case Typeface.NORMAL:
            case Typeface.BOLD:
            case Typeface.ITALIC:
            case Typeface.BOLD_ITALIC:
                break;
            default:
                return false;
        }

        if (start > end) {
            return false;
        }

        if (start == end) {
            if (start - 1 < 0 || start + 1 > getEditableText().length()) {
                return false;
            } else {
                StyleSpan[] before = getEditableText().getSpans(start - 1, start, StyleSpan.class);
                StyleSpan[] after = getEditableText().getSpans(start, start + 1, StyleSpan.class);
                return before.length > 0 && after.length > 0 && before[0].getStyle() == style && after[0].getStyle() == style;
            }
        } else {
            StringBuilder builder = new StringBuilder();

            // Make sure no duplicate characters be added
            for (int i = start; i < end; i++) {
                StyleSpan[] spans = getEditableText().getSpans(i, i + 1, StyleSpan.class);
                for (StyleSpan span : spans) {
                    if (span.getStyle() == style) {
                        builder.append(getEditableText().subSequence(i, i + 1));
                        break;
                    }
                }
            }

            return getEditableText().subSequence(start, end).toString().equals(builder.toString());
        }
    }

    // TextColor ===============================================================================

    public void textColor(String colorHex, boolean valid) {
        if (valid) {
            styleTextColorValid(colorHex, getSelectionStart(), getSelectionEnd());
        } else {
            styleTextColorInvalid(getSelectionStart(), getSelectionEnd());
        }
    }

    protected void styleTextColorValid(String colorHex, int start, int end) {
        if (start >= end) {
            return;
        }

        getEditableText().setSpan(new ForegroundColorSpan(Color.parseColor(colorHex)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    protected void styleTextColorValid(int color, int start, int end) {
        if (start >= end) {
            return;
        }

        getEditableText().setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    protected void styleTextColorInvalid(int start, int end) {
        if (start >= end) {
            return;
        }

        ForegroundColorSpan[] spans = getEditableText().getSpans(start, end, ForegroundColorSpan.class);
        List<KnifePart> list = new ArrayList<>();

        for (ForegroundColorSpan span : spans) {
            list.add(new KnifePart(span.getForegroundColor(), getEditableText().getSpanStart(span), getEditableText().getSpanEnd(span)));
            getEditableText().removeSpan(span);
        }

        for (KnifePart part : list) {
            if (part.isValid()) {
                if (part.getStart() < start) {
                    styleTextColorValid(part.getValue(), part.getStart(), start);
                }

                if (part.getEnd() > end) {
                    styleTextColorValid(part.getValue(), end, part.getEnd());
                }
            }
        }
    }

    protected boolean containTextColor(int start, int end) {
        if (start > end) {
            return false;
        }

        if (start == end) {
            if (start - 1 < 0 || start + 1 > getEditableText().length()) {
                return false;
            } else {
                ForegroundColorSpan[] before = getEditableText().getSpans(start - 1, start, ForegroundColorSpan.class);
                ForegroundColorSpan[] after = getEditableText().getSpans(start, start + 1, ForegroundColorSpan.class);
                return before.length > 0 && after.length > 0;
            }
        } else {
            StringBuilder builder = new StringBuilder();

            for (int i = start; i < end; i++) {
                if (getEditableText().getSpans(i, i + 1, ForegroundColorSpan.class).length > 0) {
                    builder.append(getEditableText().subSequence(i, i + 1));
                }
            }

            return getEditableText().subSequence(start, end).toString().equals(builder.toString());
        }
    }


    // Heading ===============================================================================

    public void headingTag(HeadingTagDefault headingTagDefault, boolean valid) {
        if (valid) {
            styleHeadingTagValid(headingTagDefault, getSelectionStart(), getSelectionEnd());
        } else {
            styleHeadingTagInvalid(getSelectionStart(), getSelectionEnd());
        }
    }

    protected void styleHeadingTagValid(HeadingTagDefault headingTagDefault, int start, int end) {
        if (start >= end) {
            return;
        }

        getEditableText().setSpan(new RelativeSizeSpan(headingTagDefault.getValue()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    protected void styleHeadingTagInvalid(int start, int end) {
        if (start >= end) {
            return;
        }

        RelativeSizeSpan[] spans = getEditableText().getSpans(start, end, RelativeSizeSpan.class);
        List<KnifePart> list = new ArrayList<>();

        for (RelativeSizeSpan span : spans) {
            list.add(new KnifePart(span.getSizeChange(), getEditableText().getSpanStart(span), getEditableText().getSpanEnd(span)));
            getEditableText().removeSpan(span);
        }

        for (KnifePart part : list) {
            if (part.isValid()) {
                if (part.getStart() < start) {
                    styleHeadingTagValid(HeadingTagDefault.get(part.getValueFloat()), part.getStart(), start);
                }

                if (part.getEnd() > end) {
                    styleHeadingTagValid(HeadingTagDefault.get(part.getValueFloat()), end, part.getEnd());
                }
            }
        }
    }

    protected boolean containHeadingTag(int start, int end) {
        if (start > end) {
            return false;
        }

        if (start == end) {
            if (start - 1 < 0 || start + 1 > getEditableText().length()) {
                return false;
            } else {
                RelativeSizeSpan[] before = getEditableText().getSpans(start - 1, start, RelativeSizeSpan.class);
                RelativeSizeSpan[] after = getEditableText().getSpans(start, start + 1, RelativeSizeSpan.class);
                return before.length > 0 && after.length > 0;
            }
        } else {
            StringBuilder builder = new StringBuilder();

            for (int i = start; i < end; i++) {
                if (getEditableText().getSpans(i, i + 1, RelativeSizeSpan.class).length > 0) {
                    builder.append(getEditableText().subSequence(i, i + 1));
                }
            }

            return getEditableText().subSequence(start, end).toString().equals(builder.toString());
        }
    }

    // Heading ===============================================================================

    public void aligning(AligningDefault aligningDefault, boolean valid) {
        if (valid) {
            styleAligningValid(aligningDefault, getSelectionStart(), getSelectionEnd());
        } else {
            styleAligningInvalid(getSelectionStart(), getSelectionEnd());
        }
    }

    protected void styleAligningValid(AligningDefault aligningDefault, int start, int end) {
        if (start >= end) {
            return;
        }

        getEditableText().setSpan(new AlignmentSpan(aligningDefault.getValue()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    protected void styleAligningInvalid(int start, int end) {
        if (start >= end) {
            return;
        }

        AlignmentSpan[] spans = getEditableText().getSpans(start, end, AlignmentSpan.class);
        List<KnifePart> list = new ArrayList<>();

        for (AlignmentSpan span : spans) {
            list.add(new KnifePart(span.getValue(), getEditableText().getSpanStart(span), getEditableText().getSpanEnd(span)));
            getEditableText().removeSpan(span);
        }

        for (KnifePart part : list) {
            if (part.isValid()) {
                if (part.getStart() < start) {
                    styleAligningValid(AligningDefault.get(part.getValue()), part.getStart(), start);
                }

                if (part.getEnd() > end) {
                    styleAligningValid(AligningDefault.get(part.getValue()), end, part.getEnd());
                }
            }
        }
    }

    protected boolean containAligning(int start, int end) {
        if (start > end) {
            return false;
        }

        if (start == end) {
            if (start - 1 < 0 || start + 1 > getEditableText().length()) {
                return false;
            } else {
                ParagraphStyle[] before = getEditableText().getSpans(start - 1, start, ParagraphStyle.class);
                ParagraphStyle[] after = getEditableText().getSpans(start, start + 1, ParagraphStyle.class);
                return before.length > 0 && after.length > 0;
            }
        } else {
            StringBuilder builder = new StringBuilder();

            for (int i = start; i < end; i++) {
                if (getEditableText().getSpans(i, i + 1, ParagraphStyle.class).length > 0) {
                    builder.append(getEditableText().subSequence(i, i + 1));
                }
            }

            return getEditableText().subSequence(start, end).toString().equals(builder.toString());
        }
    }

    // UnderlineSpan ===============================================================================

    public void underline(boolean valid) {
        if (valid) {
            underlineValid(getSelectionStart(), getSelectionEnd());
        } else {
            underlineInvalid(getSelectionStart(), getSelectionEnd());
        }
    }

    protected void underlineValid(int start, int end) {
        if (start >= end) {
            return;
        }

        getEditableText().setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    protected void underlineInvalid(int start, int end) {
        if (start >= end) {
            return;
        }

        UnderlineSpan[] spans = getEditableText().getSpans(start, end, UnderlineSpan.class);
        List<KnifePart> list = new ArrayList<>();

        for (UnderlineSpan span : spans) {
            list.add(new KnifePart(getEditableText().getSpanStart(span), getEditableText().getSpanEnd(span)));
            getEditableText().removeSpan(span);
        }

        for (KnifePart part : list) {
            if (part.isValid()) {
                if (part.getStart() < start) {
                    underlineValid(part.getStart(), start);
                }

                if (part.getEnd() > end) {
                    underlineValid(end, part.getEnd());
                }
            }
        }
    }

    protected boolean containUnderline(int start, int end) {
        if (start > end) {
            return false;
        }

        if (start == end) {
            if (start - 1 < 0 || start + 1 > getEditableText().length()) {
                return false;
            } else {
                UnderlineSpan[] before = getEditableText().getSpans(start - 1, start, UnderlineSpan.class);
                UnderlineSpan[] after = getEditableText().getSpans(start, start + 1, UnderlineSpan.class);
                return before.length > 0 && after.length > 0;
            }
        } else {
            StringBuilder builder = new StringBuilder();

            for (int i = start; i < end; i++) {
                if (getEditableText().getSpans(i, i + 1, UnderlineSpan.class).length > 0) {
                    builder.append(getEditableText().subSequence(i, i + 1).toString());
                }
            }

            return getEditableText().subSequence(start, end).toString().equals(builder.toString());
        }
    }

    // StrikethroughSpan ===========================================================================

    public void strikethrough(boolean valid) {
        if (valid) {
            strikethroughValid(getSelectionStart(), getSelectionEnd());
        } else {
            strikethroughInvalid(getSelectionStart(), getSelectionEnd());
        }
    }

    protected void strikethroughValid(int start, int end) {
        if (start >= end) {
            return;
        }

        getEditableText().setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    protected void strikethroughInvalid(int start, int end) {
        if (start >= end) {
            return;
        }

        StrikethroughSpan[] spans = getEditableText().getSpans(start, end, StrikethroughSpan.class);
        List<KnifePart> list = new ArrayList<>();

        for (StrikethroughSpan span : spans) {
            list.add(new KnifePart(getEditableText().getSpanStart(span), getEditableText().getSpanEnd(span)));
            getEditableText().removeSpan(span);
        }

        for (KnifePart part : list) {
            if (part.isValid()) {
                if (part.getStart() < start) {
                    strikethroughValid(part.getStart(), start);
                }

                if (part.getEnd() > end) {
                    strikethroughValid(end, part.getEnd());
                }
            }
        }
    }

    protected boolean containStrikethrough(int start, int end) {
        if (start > end) {
            return false;
        }

        if (start == end) {
            if (start - 1 < 0 || start + 1 > getEditableText().length()) {
                return false;
            } else {
                StrikethroughSpan[] before = getEditableText().getSpans(start - 1, start, StrikethroughSpan.class);
                StrikethroughSpan[] after = getEditableText().getSpans(start, start + 1, StrikethroughSpan.class);
                return before.length > 0 && after.length > 0;
            }
        } else {
            StringBuilder builder = new StringBuilder();

            for (int i = start; i < end; i++) {
                if (getEditableText().getSpans(i, i + 1, StrikethroughSpan.class).length > 0) {
                    builder.append(getEditableText().subSequence(i, i + 1).toString());
                }
            }

            return getEditableText().subSequence(start, end).toString().equals(builder.toString());
        }
    }

    // BulletSpan ==================================================================================

    public void bullet(boolean valid) {
        if (valid) {
            bulletValid();
        } else {
            bulletInvalid();
        }
    }

    protected void bulletValid() {
        String[] lines = TextUtils.split(getEditableText().toString(), "\n");

        for (int i = 0; i < lines.length; i++) {
            if (containBullet(i)) {
                continue;
            }

            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart = lineStart + lines[j].length() + 1; // \n
            }

            int lineEnd = lineStart + lines[i].length();
            if (lineStart >= lineEnd) {
                continue;
            }

            // Find selection area inside
            int bulletStart = 0;
            int bulletEnd = 0;
            if (lineStart <= getSelectionStart() && getSelectionEnd() <= lineEnd) {
                bulletStart = lineStart;
                bulletEnd = lineEnd;
            } else if (getSelectionStart() <= lineStart && lineEnd <= getSelectionEnd()) {
                bulletStart = lineStart;
                bulletEnd = lineEnd;
            }

            if (bulletStart < bulletEnd) {
                getEditableText().setSpan(new KnifeBulletSpan(bulletColor, bulletRadius, bulletGapWidth), bulletStart, bulletEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    protected void bulletInvalid() {
        String[] lines = TextUtils.split(getEditableText().toString(), "\n");

        for (int i = 0; i < lines.length; i++) {
            if (!containBullet(i)) {
                continue;
            }

            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart = lineStart + lines[j].length() + 1;
            }

            int lineEnd = lineStart + lines[i].length();
            if (lineStart >= lineEnd) {
                continue;
            }

            int bulletStart = 0;
            int bulletEnd = 0;
            if (lineStart <= getSelectionStart() && getSelectionEnd() <= lineEnd) {
                bulletStart = lineStart;
                bulletEnd = lineEnd;
            } else if (getSelectionStart() <= lineStart && lineEnd <= getSelectionEnd()) {
                bulletStart = lineStart;
                bulletEnd = lineEnd;
            }

            if (bulletStart < bulletEnd) {
                BulletSpan[] spans = getEditableText().getSpans(bulletStart, bulletEnd, BulletSpan.class);
                for (BulletSpan span : spans) {
                    getEditableText().removeSpan(span);
                }
            }
        }
    }

    protected boolean containBullet() {
        String[] lines = TextUtils.split(getEditableText().toString(), "\n");
        List<Integer> list = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart = lineStart + lines[j].length() + 1;
            }

            int lineEnd = lineStart + lines[i].length();
            if (lineStart >= lineEnd) {
                continue;
            }

            if (lineStart <= getSelectionStart() && getSelectionEnd() <= lineEnd) {
                list.add(i);
            } else if (getSelectionStart() <= lineStart && lineEnd <= getSelectionEnd()) {
                list.add(i);
            }
        }

        for (Integer i : list) {
            if (!containBullet(i)) {
                return false;
            }
        }

        return true;
    }

    protected boolean containBullet(int index) {
        String[] lines = TextUtils.split(getEditableText().toString(), "\n");
        if (index < 0 || index >= lines.length) {
            return false;
        }

        int start = 0;
        for (int i = 0; i < index; i++) {
            start = start + lines[i].length() + 1;
        }

        int end = start + lines[index].length();
        if (start >= end) {
            return false;
        }

        BulletSpan[] spans = getEditableText().getSpans(start, end, BulletSpan.class);
        return spans.length > 0;
    }

    // QuoteSpan ===================================================================================

    public void quote(boolean valid) {
        if (valid) {
            quoteValid();
        } else {
            quoteInvalid();
        }
    }

    protected void quoteValid() {
        String[] lines = TextUtils.split(getEditableText().toString(), "\n");

        for (int i = 0; i < lines.length; i++) {
            if (containQuote(i)) {
                continue;
            }

            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart = lineStart + lines[j].length() + 1; // \n
            }

            int lineEnd = lineStart + lines[i].length();
            if (lineStart >= lineEnd) {
                continue;
            }

            int quoteStart = 0;
            int quoteEnd = 0;
            if (lineStart <= getSelectionStart() && getSelectionEnd() <= lineEnd) {
                quoteStart = lineStart;
                quoteEnd = lineEnd;
            } else if (getSelectionStart() <= lineStart && lineEnd <= getSelectionEnd()) {
                quoteStart = lineStart;
                quoteEnd = lineEnd;
            }

            if (quoteStart < quoteEnd) {
                getEditableText().setSpan(new KnifeQuoteSpan(quoteColor, quoteStripeWidth, quoteGapWidth), quoteStart, quoteEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    protected void quoteInvalid() {
        String[] lines = TextUtils.split(getEditableText().toString(), "\n");

        for (int i = 0; i < lines.length; i++) {
            if (!containQuote(i)) {
                continue;
            }

            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart = lineStart + lines[j].length() + 1;
            }

            int lineEnd = lineStart + lines[i].length();
            if (lineStart >= lineEnd) {
                continue;
            }

            int quoteStart = 0;
            int quoteEnd = 0;
            if (lineStart <= getSelectionStart() && getSelectionEnd() <= lineEnd) {
                quoteStart = lineStart;
                quoteEnd = lineEnd;
            } else if (getSelectionStart() <= lineStart && lineEnd <= getSelectionEnd()) {
                quoteStart = lineStart;
                quoteEnd = lineEnd;
            }

            if (quoteStart < quoteEnd) {
                QuoteSpan[] spans = getEditableText().getSpans(quoteStart, quoteEnd, QuoteSpan.class);
                for (QuoteSpan span : spans) {
                    getEditableText().removeSpan(span);
                }
            }
        }
    }

    protected boolean containQuote() {
        String[] lines = TextUtils.split(getEditableText().toString(), "\n");
        List<Integer> list = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart = lineStart + lines[j].length() + 1;
            }

            int lineEnd = lineStart + lines[i].length();
            if (lineStart >= lineEnd) {
                continue;
            }

            if (lineStart <= getSelectionStart() && getSelectionEnd() <= lineEnd) {
                list.add(i);
            } else if (getSelectionStart() <= lineStart && lineEnd <= getSelectionEnd()) {
                list.add(i);
            }
        }

        for (Integer i : list) {
            if (!containQuote(i)) {
                return false;
            }
        }

        return true;
    }

    protected boolean containQuote(int index) {
        String[] lines = TextUtils.split(getEditableText().toString(), "\n");
        if (index < 0 || index >= lines.length) {
            return false;
        }

        int start = 0;
        for (int i = 0; i < index; i++) {
            start = start + lines[i].length() + 1;
        }

        int end = start + lines[index].length();
        if (start >= end) {
            return false;
        }

        QuoteSpan[] spans = getEditableText().getSpans(start, end, QuoteSpan.class);
        return spans.length > 0;
    }

    // URLSpan =====================================================================================

    public void link(String link) {
        link(link, getSelectionStart(), getSelectionEnd());
    }

    // When KnifeText lose focus, use this method
    public void link(String link, int start, int end) {
        if (link != null && !TextUtils.isEmpty(link.trim())) {
            linkValid(link, start, end);
        } else {
            linkInvalid(start, end);
        }
    }

    protected void linkValid(String link, int start, int end) {
        if (start >= end) {
            return;
        }

        linkInvalid(start, end);
        getEditableText().setSpan(new KnifeURLSpan(link, linkColor, linkUnderline), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // Remove all span in selection, not like the boldInvalid()
    protected void linkInvalid(int start, int end) {
        if (start >= end) {
            return;
        }

        URLSpan[] spans = getEditableText().getSpans(start, end, URLSpan.class);
        for (URLSpan span : spans) {
            getEditableText().removeSpan(span);
        }
    }

    protected boolean containLink(int start, int end) {
        if (start > end) {
            return false;
        }

        if (start == end) {
            if (start - 1 < 0 || start + 1 > getEditableText().length()) {
                return false;
            } else {
                URLSpan[] before = getEditableText().getSpans(start - 1, start, URLSpan.class);
                URLSpan[] after = getEditableText().getSpans(start, start + 1, URLSpan.class);
                return before.length > 0 && after.length > 0;
            }
        } else {
            StringBuilder builder = new StringBuilder();

            for (int i = start; i < end; i++) {
                if (getEditableText().getSpans(i, i + 1, URLSpan.class).length > 0) {
                    builder.append(getEditableText().subSequence(i, i + 1).toString());
                }
            }

            return getEditableText().subSequence(start, end).toString().equals(builder.toString());
        }
    }


    // Image ===============================================================================

    public void image(final String path, final int maxWidth) {
        //noinspection deprecation
        glideRequests.asBitmap()
                .load(new File(path))
                .centerCrop()
                .error(R.drawable.fill_img)
                .placeholder(R.drawable.fill_img)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        Bitmap bitmap = BitmapUtil.zoomBitmapToFixWidth(resource, maxWidth);
                        image(path, bitmap);
                    }
                });
    }

    public void image(final String path) {
        int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        image(path, width);
    }

    public void image(String path, Bitmap pic) {
        SpannableString ss = new SpannableString(" \n");
        ImageCustomSpan span = new ImageCustomSpan(getContext(), pic, path, MediaImageType.FILE);
        ss.setSpan(span, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int start = getSelectionStart();
        getEditableText().insert(start, ss);
    }

    //image -> Uri

    public void image(final Uri uri, final int maxWidth) {
        //noinspection deprecation
        glideRequests.asBitmap()
                .load(uri)
                .centerCrop()
                .error(R.drawable.fill_img)
                .placeholder(R.drawable.fill_img)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                        Bitmap bitmap = BitmapUtil.zoomBitmapToFixWidth(resource, maxWidth);
                        image(uri, bitmap);
                    }
                });
    }

    public void image(final Uri uri) {
        int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        image(uri, width);
    }

    public void image(Uri uri, Bitmap pic) {
        SpannableString ss = new SpannableString(" \n");
        ImageCustomSpan span = new ImageCustomSpan(getContext(), pic, uri, MediaImageType.URI);
        ss.setSpan(span, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int start = getSelectionStart();
        getEditableText().insert(start, ss);
    }


    // Redo/Undo ===================================================================================

    @Override
    public void beforeTextChanged(CharSequence text, int start, int count, int after) {
        if (!historyEnable || historyWorking) {
            return;
        }

        inputBefore = new SpannableStringBuilder(text);
    }

    @Override
    public void onTextChanged(CharSequence text, int start, int before, int count) {
        // DO NOTHING HERE
    }

    @Override
    public void afterTextChanged(Editable text) {
        if (!historyEnable || historyWorking) {
            return;
        }

        inputLast = new SpannableStringBuilder(text);
        if (text != null && text.toString().equals(inputBefore.toString())) {
            return;
        }

        if (historyList.size() >= historySize) {
            historyList.remove(0);
        }

        historyList.add(inputBefore);
        historyCursor = historyList.size();
    }

    public void redo() {
        if (!redoValid()) {
            return;
        }

        historyWorking = true;

        if (historyCursor >= historyList.size() - 1) {
            historyCursor = historyList.size();
            setText(inputLast);
        } else {
            historyCursor++;
            setText(historyList.get(historyCursor));
        }

        setSelection(getEditableText().length());
        historyWorking = false;
    }

    public void undo() {
        if (!undoValid()) {
            return;
        }

        historyWorking = true;

        historyCursor--;
        setText(historyList.get(historyCursor));
        setSelection(getEditableText().length());

        historyWorking = false;
    }

    public boolean redoValid() {
        if (!historyEnable || historySize <= 0 || historyList.size() <= 0 || historyWorking) {
            return false;
        }

        return historyCursor < historyList.size() - 1 || historyCursor >= historyList.size() - 1 && inputLast != null;
    }

    public boolean undoValid() {
        if (!historyEnable || historySize <= 0 || historyWorking) {
            return false;
        }

        if (historyList.size() <= 0 || historyCursor <= 0) {
            return false;
        }

        return true;
    }

    public void clearHistory() {
        if (historyList != null) {
            historyList.clear();
        }
    }

    // Helper ======================================================================================

    public boolean contains(int format) {
        switch (format) {
            case FORMAT_BOLD:
                return containStyle(Typeface.BOLD, getSelectionStart(), getSelectionEnd());
            case FORMAT_ITALIC:
                return containStyle(Typeface.ITALIC, getSelectionStart(), getSelectionEnd());
            case FORMAT_UNDERLINED:
                return containUnderline(getSelectionStart(), getSelectionEnd());
            case FORMAT_STRIKETHROUGH:
                return containStrikethrough(getSelectionStart(), getSelectionEnd());
            case FORMAT_BULLET:
                return containBullet();
            case FORMAT_QUOTE:
                return containQuote();
            case FORMAT_LINK:
                return containLink(getSelectionStart(), getSelectionEnd());
            case TEXT_COLOR:
                return containTextColor(getSelectionStart(), getSelectionEnd());
            case HEADING_TAG:
                return containHeadingTag(getSelectionStart(), getSelectionEnd());
            case TEXT_ALIGN:
                return containAligning(getSelectionStart(), getSelectionEnd());
            default:
                return false;
        }
    }

    public void clearFormats() {
        setText(getEditableText().toString());
        setSelection(getEditableText().length());
    }

    public void hideSoftInput() {
        clearFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    public void showSoftInput() {
        requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    public void fromHtml(String source) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(KnifeParser.fromHtml(source, new GlideImageGetter(this, glideRequests)));
        switchToKnifeStyle(builder, 0, builder.length());
        setText(builder);
    }

    public String toHtml() {
        return KnifeParser.toHtml(getEditableText());
    }

    protected void switchToKnifeStyle(Editable editable, int start, int end) {
        BulletSpan[] bulletSpans = editable.getSpans(start, end, BulletSpan.class);
        for (BulletSpan span : bulletSpans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            spanEnd = 0 < spanEnd && spanEnd < editable.length() && editable.charAt(spanEnd) == '\n' ? spanEnd - 1 : spanEnd;
            editable.removeSpan(span);
            editable.setSpan(new KnifeBulletSpan(bulletColor, bulletRadius, bulletGapWidth), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        QuoteSpan[] quoteSpans = editable.getSpans(start, end, QuoteSpan.class);
        for (QuoteSpan span : quoteSpans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            spanEnd = 0 < spanEnd && spanEnd < editable.length() && editable.charAt(spanEnd) == '\n' ? spanEnd - 1 : spanEnd;
            editable.removeSpan(span);
            editable.setSpan(new KnifeQuoteSpan(quoteColor, quoteStripeWidth, quoteGapWidth), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        URLSpan[] urlSpans = editable.getSpans(start, end, URLSpan.class);
        for (URLSpan span : urlSpans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            editable.removeSpan(span);
            editable.setSpan(new KnifeURLSpan(span.getURL(), linkColor, linkUnderline), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
