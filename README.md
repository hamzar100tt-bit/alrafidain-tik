# الرافدين تيك

تطبيق فيديوهات قصيرة باللغة العربية، مبني باستخدام React وVite وSupabase.

## ما هو موجود حالياً
- واجهة عربية متجاوبة.
- إنشاء حساب وتسجيل الدخول عبر Supabase Auth.
- تحميل التغذية من قاعدة البيانات.
- رفع فيديوهات MP4 وWebM وMOV إلى Supabase Storage (حتى 100MB).
- التعليقات والإعجابات.

## قاعدة البيانات
تم تجهيز مخطط قاعدة البيانات في `supabase/schema.sql`. تم تنفيذ المخطط على مشروع Supabase المرتبط بالتطبيق.

## بيانات الاتصال
يستخدم التطبيق رابط المشروع ومفتاح Supabase Publishable (مفتاح عام مخصص للمتصفح) من `src/lib/supabase.js`. لا تضع أبداً Secret أو service_role key في كود المتصفح.

## تشغيل محلي
```bash
npm install
npm run dev
```

## النشر على GitHub Pages
يوجد workflow في `.github/workflows/deploy.yml` يبني التطبيق وينشره عند كل تحديث على فرع `main`.

1. افتح مستودع GitHub ثم Settings ثم Pages.
2. في Build and deployment اختر Source: GitHub Actions.
3. افتح تبويب Actions وانتظر اكتمال Build and deploy Alrafidain Tik.
4. رابط الموقع سيكون: https://hamzar100tt-bit.github.io/alrafidain-tik/

## ملاحظات
- الواجهة تعرض فيديوهات تجريبية إذا لم توجد فيديوهات منشورة بعد.
- تفعيل تأكيد البريد الإلكتروني يعتمد على إعدادات Auth في Supabase.
- لم يتم بعد اختبار عملية النشر الحية أو تجربة الحساب والرفع على جهاز المستخدم.
