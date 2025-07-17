/*private String getPhoneticText(String text) {


    if (text == null) return "";



    // İlçe adları (büyük/küçük harf duyarsız)
    text = text.replaceAll("(?i)kocasinan", "Ko-ca-si-nan");
    text = text.replaceAll("(?i)melikgazi", "Me-lik-ga-zi");
    text = text.replaceAll("(?i)talas", "Ta-las");
    text = text.replaceAll("(?i)hacılar", "Ha-cı-lar");
    text = text.replaceAll("(?i)özvatan", "Öz-va-tan");
    text = text.replaceAll("(?i)akkışla", "Ak-kış-la");
    text = text.replaceAll("(?i)bünyan", "Bün-yan");
    text = text.replaceAll("(?i)develi", "De-ve-li");
    text = text.replaceAll("(?i)felahiye", "Fe-la-hi-ye");
    text = text.replaceAll("(?i)incesu", "İn-ce-su");
    text = text.replaceAll("(?i)pınarbaşı", "Pı-nar-ba-şı");
    text = text.replaceAll("(?i)sarıoğlan", "Sa-rı-oğ-lan");
    text = text.replaceAll("(?i)sarız", "Sa-rız");
    text = text.replaceAll("(?i)tomarza", "To-mar-za");
    text = text.replaceAll("(?i)yahyalı", "Yah-ya-lı");
    text = text.replaceAll("(?i)yeşilhisar", "Ye-şil-hi-sar");

    // Mahalle adları - Mevcut olanlar
    text = text.replaceAll("(?i)yeniköy", "Ye-ni-köy");
    text = text.replaceAll("(?i)esentepe", "E-sen-te-pe");
    text = text.replaceAll("(?i)fevzi çakmak", "Fev-zi Çak-mak");
    text = text.replaceAll("(?i)ismet paşa", "İs-met Pa-şa");
    text = text.replaceAll("(?i)yıldırım beyazıt", "Yıl-dı-rım Be-ya-zıt");
    text = text.replaceAll("(?i)erciyes", "Er-ci-yes");
    text = text.replaceAll("(?i)zümrüt", "Züm-rüt");
    text = text.replaceAll("(?i)bahçelievler", "Bah-çe-li-ev-ler");
    text = text.replaceAll("(?i)anbar", "An-bar");
    text = text.replaceAll("(?i)eki̇nli̇k", "E-kin-lik");
    text = text.replaceAll("(?i)gültepe", "Gül-te-pe");
    text = text.replaceAll("(?i)sanayi", "Sa-na-yi");
    text = text.replaceAll("(?i)kayabasi", "Ka-ya-ba-şı");

    // Ek mahalle adları
    text = text.replaceAll("(?i)alpaslan", "Al-pas-lan");
    text = text.replaceAll("(?i)atatürk", "A-ta-türk");
    text = text.replaceAll("(?i)barbaros", "Bar-ba-ros");
    text = text.replaceAll("(?i)bedir", "Be-dir");
    text = text.replaceAll("(?i)belleten", "Bel-le-ten");
    text = text.replaceAll("(?i)beyazşehir", "Be-yaz-şe-hir");
    text = text.replaceAll("(?i)camilikebir", "Ca-mi-li-ke-bir");
    text = text.replaceAll("(?i)cumhuriyet", "Cum-hu-ri-yet");
    text = text.replaceAll("(?i)dumlupınar", "Dum-lu-pı-nar");
    text = text.replaceAll("(?i)emek", "E-mek");
    text = text.replaceAll("(?i)eskikale", "Es-ki-ka-le");
    text = text.replaceAll("(?i)fatih", "Fa-tih");
    text = text.replaceAll("(?i)gazi", "Ga-zi");
    text = text.replaceAll("(?i)gürpınar", "Gür-pı-nar");
    text = text.replaceAll("(?i)hürriyet", "Hür-ri-yet");
    text = text.replaceAll("(?i)istiklal", "İs-tik-lal");
    text = text.replaceAll("(?i)kale", "Ka-le");
    text = text.replaceAll("(?i)kapıkule", "Ka-pı-ku-le");
    text = text.replaceAll("(?i)kartal", "Kar-tal");
    text = text.replaceAll("(?i)kayseri", "Kay-se-ri");
    text = text.replaceAll("(?i)kılıçarslan", "Kı-lı-çars-lan");
    text = text.replaceAll("(?i)kocatepe", "Ko-ca-te-pe");
    text = text.replaceAll("(?i)köşk", "Köşk");
    text = text.replaceAll("(?i)kültür", "Kül-tür");
    text = text.replaceAll("(?i)mahirbaba", "Ma-hir-ba-ba");
    text = text.replaceAll("(?i)mimar sinan", "Mi-mar Si-nan");
    text = text.replaceAll("(?i)molla gürani", "Mol-la Gü-ra-ni");
    text = text.replaceAll("(?i)mustafakemalpaşa", "Mus-ta-fa-ke-mal-pa-şa");
    text = text.replaceAll("(?i)osman kavuncu", "Os-man Ka-vun-cu");
    text = text.replaceAll("(?i)oruçreis", "O-ruç-re-is");
    text = text.replaceAll("(?i)sahabiye", "Sa-ha-bi-ye");
    text = text.replaceAll("(?i)sakarya", "Sa-kar-ya");
    text = text.replaceAll("(?i)selçuk", "Sel-çuk");
    text = text.replaceAll("(?i)seyyid burhanettin", "Sey-yid Bur-ha-net-tin");
    text = text.replaceAll("(?i)şehit", "Şe-hit");
    text = text.replaceAll("(?i)şehitfevzi", "Şe-hit-fev-zi");
    text = text.replaceAll("(?i)tacettin veli", "Ta-cet-tin Ve-li");
    text = text.replaceAll("(?i)turgut özal", "Tur-gut Ö-zal");
    text = text.replaceAll("(?i)uğurevler", "U-ğur-ev-ler");
    text = text.replaceAll("(?i)vatan", "Va-tan");
    text = text.replaceAll("(?i)yenice", "Ye-ni-ce");
    text = text.replaceAll("(?i)yıldız", "Yıl-dız");
    text = text.replaceAll("(?i)yurt", "Yurt");
    text = text.replaceAll("(?i)zafer", "Za-fer");
    text = text.replaceAll("(?i)ziya gökalp", "Zi-ya Gö-kalp");

    // Talas ilçesi mahalleleri
    text = text.replaceAll("(?i)harman", "Har-man");
    text = text.replaceAll("(?i)talas merkez", "Ta-las Mer-kez");
    text = text.replaceAll("(?i)ağırnas", "A-ğır-nas");
    text = text.replaceAll("(?i)çamlık", "Çam-lık");
    text = text.replaceAll("(?i)büyük bürüngüz", "Bü-yük Bü-rün-güz");
    text = text.replaceAll("(?i)küçük bürüngüz", "Kü-çük Bü-rün-güz");
    text = text.replaceAll("(?i)sarımsaklı", "Sa-rım-sak-lı");
    text = text.replaceAll("(?i)gesi", "Ge-si");
    text = text.replaceAll("(?i)karayakup", "Ka-ra-ya-kup");
    text = text.replaceAll("(?i)bahşılı", "Bah-şı-lı");

    // Hacılar ilçesi mahalleleri
    text = text.replaceAll("(?i)hacılar merkez", "Ha-cı-lar Mer-kez");
    text = text.replaceAll("(?i)karakoyunlu", "Ka-ra-ko-yun-lu");
    text = text.replaceAll("(?i)kayalar", "Ka-ya-lar");
    text = text.replaceAll("(?i)emirhacı", "E-mir-ha-cı");
    text = text.replaceAll("(?i)hacıabdullah", "Ha-cı-ab-dul-lah");
    text = text.replaceAll("(?i)örenşehir", "Ö-ren-şe-hir");

    // Bünyan ilçesi mahalleleri
    text = text.replaceAll("(?i)bünyan merkez", "Bün-yan Mer-kez");
    text = text.replaceAll("(?i)karakuyu", "Ka-ra-ku-yu");
    text = text.replaceAll("(?i)büyükkolukısa", "Bü-yük-ko-lu-kı-sa");
    text = text.replaceAll("(?i)küçükkolukısa", "Kü-çük-ko-lu-kı-sa");
    text = text.replaceAll("(?i)yeşilova", "Ye-şil-o-va");
    text = text.replaceAll("(?i)argıncık", "Ar-gın-cık");

    // Develi ilçesi mahalleleri
    text = text.replaceAll("(?i)develi merkez", "De-ve-li Mer-kez");
    text = text.replaceAll("(?i)sindelhöyük", "Sin-del-hö-yük");
    text = text.replaceAll("(?i)yeşilhisar", "Ye-şil-hi-sar");
    text = text.replaceAll("(?i)soğanlı", "So-ğan-lı");
    text = text.replaceAll("(?i)avanos", "A-va-nos");

    // İncesu ilçesi mahalleleri
    text = text.replaceAll("(?i)incesu merkez", "İn-ce-su Mer-kez");
    text = text.replaceAll("(?i)dokuzun", "Do-ku-zun");
    text = text.replaceAll("(?i)tuzkışla", "Tuz-kış-la");
    text = text.replaceAll("(?i)büyükbürüngüz", "Bü-yük-bü-rün-güz");

    // Tire yerine duraklama için virgül
    text = text.replaceAll(" - ", ", ");

    return text;
}*/