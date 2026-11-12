"""Locally authored practice visual, shared by the IELTS LaTeX export."""
import io
from PIL import Image, ImageDraw, ImageFont


def commuting_png():
    image = Image.new('RGB', (840, 490), 'white')
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default(size=20)
    title = ImageFont.load_default(size=26)
    draw.text((420, 28), 'Commuting methods in a city (%)', font=title, fill='#24352e', anchor='mm')
    for number in (0, 20, 40, 60):
        y = 400 - number * 4.6
        draw.line((80, y, 780, y), fill='#cbd5cf', width=1)
        draw.text((60, y), str(number), font=font, fill='#24352e', anchor='rm')
    for index, values in enumerate(((60, 45), (25, 30), (15, 25))):
        for offset, (value, color) in enumerate(zip(values, ('#216e4b', '#9cbda8'))):
            x = 140 + index * 220 + offset * 60
            draw.rectangle((x, 400 - value * 4.6, x + 50, 400), fill=color)
        draw.text((196 + index * 220, 438), ('Car', 'Bus', 'Bicycle')[index], font=font, fill='#24352e', anchor='mm')
    for x, color, label in ((520, '#216e4b', '2000'), (660, '#9cbda8', '2020')):
        draw.rectangle((x, 76, x + 16, 92), fill=color)
        draw.text((x + 24, 83), label, font=font, fill='#24352e', anchor='lm')
    result = io.BytesIO()
    image.save(result, 'PNG')
    return result.getvalue()
