#!/data/data/com.termux/files/usr/bin/bash
pkg update -y && pkg install python android-tools -y
pip install termuxgui
mkdir -p ~/.shortcuts
cat << 'END' > ~/.shortcuts/Granny_AutoBot
#!/data/data/com.termux/files/usr/bin/bash
cd ~/GrannyAutoMacro
python granny_app.py
END
chmod +x ~/.shortcuts/Granny_AutoBot
chmod +x granny_app.py
echo "✅ Shortcut & Instalasi Selesai!"
