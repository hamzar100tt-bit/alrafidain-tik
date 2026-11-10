# الرافدين تيك

تطبيق فيديوهات قصيرة باللغة العربية، مبني باستخدام React وVite وSupabase وCapacitor.

## الموجود في المستودع
- واجهة عربية متجاوبة بتصميم مخصص للهاتف.
- التسجيل وتسجيل الدخول عبر Supabase Auth.
- عرض الفيديوهات المنشورة من قاعدة البيانات.
- رفع فيديوهات MP4 وWebM وMOV إلى Supabase Storage (حتى 100MB).
- التعليقات والإعجابات والمتابعة والملف الشخصي والرسائل الخاصة.
- واجهة بث مباشر تعتمد على LiveKit، وتحتاج إعداد خدمة LiveKit قبل التشغيل الفعلي.

## إعداد Supabase لأول مرة
1. افتح مشروع Supabase الصحيح.
2. من القائمة الجانبية افتح SQL Editor ثم New query.
3. افتح الملف `supabase/setup-all.sql` في GitHub، وانسخ محتواه كاملاً.
4. الصقه في SQL Editor واضغط Run.
5. من Authentication > Providers تأكد من تفعيل Email.
6. من Authentication > URL Configuration أضف رابط موقعك إلى Site URL وRedirect URLs عند الحاجة.

الملف `supabase/setup-all.sql` ينشئ جداول الحسابات والفيديوهات والتعليقات والإعجابات والمتابعة والرسائل، ويجهز مخزني الفيديوهات والصور مع سياسات الوصول (RLS). شغّله مرة واحدة بعد إنشاء مشروع Supabase، ويمكن إعادة تشغيله عند الحاجة.

## بيانات الاتصال
يقرأ التطبيق رابط Supabase ومفتاح Publishable من `src/lib/supabase.js`، ويمكن تمريرهما كمتغيرات بناء:
- `VITE_SUPABASE_URL`
- `VITE_SUPABASE_ANON_KEY`

مفتاح Publishable/anon مخصص للاستخدام في تطبيق العميل مع سياسات RLS. لا تضع أبداً مفتاح `service_role` أو أي Secret في كود المتصفح أو تطبيق Android.

## التشغيل المحلي
```bash
npm install
npm run dev
```

## النشر على GitHub Pages
يوجد workflow في `.github/workflows/deploy.yml` للبناء والنشر عند تحديث فرع `main`.
1. افتح المستودع ثم Settings ثم Pages.
2. في Build and deployment اختر Source: GitHub Actions.
3. افتح Actions وانتظر اكتمال Build and deploy Alrafidain Tik.
4. رابط الموقع: https://hamzar100tt-bit.github.io/alrafidain-tik/

## البث المباشر (LiveKit)
واجهة البث موجودة، لكن البث الحقيقي لن يعمل قبل إعداد LiveKit:
1. أنشئ مشروعاً في LiveKit Cloud وخذ WebSocket URL وAPI Key وAPI Secret.
2. انشر الدالة: `supabase functions deploy livekit-token`.
3. خزّن الأسرار على Supabase فقط: `supabase secrets set LIVEKIT_API_KEY=... LIVEKIT_API_SECRET=...`.
4. أضف إلى GitHub Actions Secrets:
   - `VITE_LIVEKIT_URL` = عنوان WebSocket الذي يبدأ عادةً بـ `wss://`.
   - `VITE_LIVEKIT_TOKEN_ENDPOINT` = `https://YOUR_PROJECT_REF.supabase.co/functions/v1/livekit-token`.
5. أعد تشغيل workflow للبناء.

لا تضع `LIVEKIT_API_SECRET` أو مفتاح `service_role` داخل التطبيق أو المستودع.
