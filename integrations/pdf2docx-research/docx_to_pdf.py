import sys
import subprocess

def convert(docx_path, pdf_path):
    ps_script = f'''
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$doc = $word.Documents.Open("{docx_path}")
$doc.SaveAs([ref]"{pdf_path}", [ref]17)
$doc.Close()
$word.Quit()
'''
    with open("temp_convert.ps1", "w", encoding="utf-8") as f:
        f.write(ps_script)
    subprocess.run(["powershell", "-ExecutionPolicy", "Bypass", "-File", "temp_convert.ps1"], check=True)

if __name__ == "__main__":
    convert(sys.argv[1], sys.argv[2])

