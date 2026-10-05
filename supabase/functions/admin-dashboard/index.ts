import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader?.startsWith("Bearer ")) {
      return json({ error: "احراز هویت لازم است." }, 401);
    }

    const token = authHeader.replace("Bearer ", "").trim();
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const adminEmails = (Deno.env.get("HESABYAR_ADMIN_EMAILS") || "")
      .split(",")
      .map((x) => x.trim().toLowerCase())
      .filter(Boolean);

    const authClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: `Bearer ${token}` } },
    });

    const { data: { user }, error: userError } = await authClient.auth.getUser(token);
    if (userError || !user?.email) {
      return json({ error: "جلسه ورود معتبر نیست." }, 401);
    }

    if (!adminEmails.includes(user.email.toLowerCase())) {
      return json({ error: "دسترسی مدیر برای این حساب فعال نیست." }, 403);
    }

    const adminClient = createClient(supabaseUrl, serviceKey);
    const requestedUserId = new URL(req.url).searchParams.get("user_id");

    const { data: usersData, error: usersError } =
      await adminClient.auth.admin.listUsers({ page: 1, perPage: 1000 });

    if (usersError) throw usersError;

    const { data: txs, error: txError } = await adminClient
      .from("transactions")
      .select("user_id,amount,type");

    if (txError) throw txError;

    const byUser = new Map<string, { count: number; income: number; expense: number }>();
    let totalIncome = 0;
    let totalExpense = 0;

    for (const tx of txs || []) {
      const amount = Number(tx.amount || 0);
      const isIncome = tx.type === "income";
      if (isIncome) totalIncome += amount;
      else totalExpense += amount;

      const current = byUser.get(tx.user_id) || { count: 0, income: 0, expense: 0 };
      current.count++;
      if (isIncome) current.income += amount;
      else current.expense += amount;
      byUser.set(tx.user_id, current);
    }

    const users = (usersData.users || []).map((u) => {
      const stats = byUser.get(u.id) || { count: 0, income: 0, expense: 0 };
      return {
        id: u.id,
        email: u.email || "",
        created_at: u.created_at,
        last_sign_in_at: u.last_sign_in_at,
        transaction_count: stats.count,
        income: stats.income,
        expense: stats.expense,
      };
    });

    if (requestedUserId) {
      const selected = users.find((u) => u.id === requestedUserId);
      if (!selected) {
        return json({ error: "کاربر پیدا نشد." }, 404);
      }

      const { data: userTransactions, error: userTxError } =
        await adminClient
          .from("transactions")
          .select("id,title,amount,type,description,transaction_date,created_at")
          .eq("user_id", requestedUserId)
          .order("created_at", { ascending: false });

      if (userTxError) throw userTxError;

      return json({
        user: selected,
        transactions: userTransactions || [],
      }, 200);
    }

    return json({
      summary: {
        users: users.length,
        transactions: txs?.length || 0,
        income: totalIncome,
        expense: totalExpense,
      },
      users,
    }, 200);
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : "خطای سرور" }, 500);
  }
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}