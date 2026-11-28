function print_box_line(text, width, alignment)
% PRINT_BOX_LINE  Print a boxed summary line with safe padding/truncation.

    if nargin < 3
        alignment = 'left';
    end

    text = char(string(text));
    text_length = strlength(string(text));
    if text_length > width
        text = char(extractBefore(string(text), width + 1));
        text_length = width;
    end

    switch alignment
        case 'center'
            pad_total = width - double(text_length);
            pad_left = floor(pad_total / 2);
            pad_right = ceil(pad_total / 2);
            line = [repmat(' ', 1, pad_left), text, repmat(' ', 1, pad_right)];
        otherwise
            line = sprintf('%-*s', width, text);
    end

    fprintf('║%s║\n', line);
end
