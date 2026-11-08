-- Fixes the ambiguous output-column/variable name in purchase_subscription_plan.
DROP FUNCTION IF EXISTS public.purchase_subscription_plan(UUID, UUID);

CREATE FUNCTION public.purchase_subscription_plan(p_user_id UUID, p_plan_id UUID)
RETURNS TABLE(success BOOLEAN, message TEXT, subscription_id UUID, new_balance NUMERIC, free_activation BOOLEAN, trial_minutes INTEGER)
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  plan_row public.subscription_plans;
  wallet_row public.wallets;
  subscription_id_value UUID;
  wallet_balance NUMERIC;
  free_claimed BOOLEAN := FALSE;
  duration_minutes_value INTEGER;
BEGIN
  SELECT * INTO plan_row
  FROM public.subscription_plans
  WHERE id = p_plan_id AND is_active = TRUE
  FOR UPDATE;

  IF NOT FOUND THEN
    RETURN QUERY SELECT FALSE, 'Subscription plan not found'::TEXT, NULL::UUID, NULL::NUMERIC, FALSE, 0;
    RETURN;
  END IF;

  IF plan_row.free_claim_once THEN
    SELECT EXISTS (
      SELECT 1 FROM public.subscription_plan_claims c
      WHERE c.user_id = p_user_id AND c.plan_id = p_plan_id
    ) INTO free_claimed;

    IF NOT free_claimed THEN
      duration_minutes_value := CASE
        WHEN plan_row.free_duration_minutes > 0 THEN plan_row.free_duration_minutes
        ELSE plan_row.duration_days * 1440
      END;

      INSERT INTO public.subscriptions(
        user_id, product_id, subscription_plan_id, status, duration_type,
        started_at, expires_at, auto_renew, will_renew
      )
      VALUES (
        p_user_id, NULL, p_plan_id, 'active', 'trial', NOW(),
        NOW() + make_interval(mins => duration_minutes_value), FALSE, FALSE
      )
      RETURNING id INTO subscription_id_value;

      INSERT INTO public.subscription_plan_claims(user_id, plan_id, subscription_id)
      VALUES (p_user_id, p_plan_id, subscription_id_value);

      INSERT INTO public.wallets(user_id)
      VALUES (p_user_id)
      ON CONFLICT (user_id) DO NOTHING;

      SELECT w.balance INTO wallet_balance
      FROM public.wallets AS w
      WHERE w.user_id = p_user_id;

      RETURN QUERY SELECT TRUE, 'Free access activated'::TEXT,
        subscription_id_value, wallet_balance, TRUE, duration_minutes_value;
      RETURN;
    ELSIF plan_row.price = 0 THEN
      INSERT INTO public.wallets(user_id)
      VALUES (p_user_id)
      ON CONFLICT (user_id) DO NOTHING;

      SELECT w.balance INTO wallet_balance
      FROM public.wallets AS w
      WHERE w.user_id = p_user_id;

      RETURN QUERY SELECT FALSE, 'This free plan has already been claimed'::TEXT,
        NULL::UUID, wallet_balance, FALSE, 0;
      RETURN;
    END IF;
  END IF;

  INSERT INTO public.wallets(user_id)
  VALUES (p_user_id)
  ON CONFLICT (user_id) DO NOTHING;

  SELECT w.* INTO wallet_row
  FROM public.wallets AS w
  WHERE w.user_id = p_user_id
  FOR UPDATE;

  IF wallet_row.balance < plan_row.price THEN
    RETURN QUERY SELECT FALSE, 'Insufficient wallet balance'::TEXT,
      NULL::UUID, wallet_row.balance, FALSE, 0;
    RETURN;
  END IF;

  INSERT INTO public.subscriptions(
    user_id, product_id, subscription_plan_id, status, duration_type,
    started_at, expires_at, auto_renew, will_renew
  )
  VALUES (
    p_user_id, NULL, p_plan_id, 'active', 'custom', NOW(),
    NOW() + make_interval(days => plan_row.duration_days), FALSE, FALSE
  )
  RETURNING id INTO subscription_id_value;

  UPDATE public.wallets AS w
  SET balance = w.balance - plan_row.price, updated_at = NOW()
  WHERE w.id = wallet_row.id
  RETURNING w.balance INTO wallet_balance;

  INSERT INTO public.wallet_transactions(
    user_id, amount, balance_after, transaction_type, description, reference_id
  )
  VALUES (
    p_user_id, -plan_row.price, wallet_balance, 'plan_purchase',
    'Subscription plan purchase', subscription_id_value
  );

  RETURN QUERY SELECT TRUE, 'Subscription plan activated'::TEXT,
    subscription_id_value, wallet_balance, FALSE, 0;
END;
$$;
